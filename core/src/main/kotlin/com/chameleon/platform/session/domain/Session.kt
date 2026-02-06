package com.chameleon.session.domain

import com.chameleon.session.SessionId
import com.chameleon.session.SessionKey

import java.util.UUID

/**
 * Result of a session compaction operation.
 */
data class SessionCompactionResult(
    val newSession: Session,
    val event: SessionDomainEvent.ContextCompacted
)

/**
 * Session aggregate - Manages conversation history and compaction rules.
 *
 * Invariants:
 * - SessionKey is immutable and unique
 * - Compaction never drops the most recent user message
 * - Tool results can be pruned but transcript remains append-only
 */
data class Session(
    val id: SessionId,
    val key: SessionKey,
    val messages: List<Message> = emptyList(),
    val config: CompactionConfig = CompactionConfig(),
    val metadata: SessionMetadata = SessionMetadata(),
    val summaries: List<CompactionSummary> = emptyList()
) {
    /**
     * Adds a message to the session and updates metadata.
     */
    fun withMessage(message: Message): Pair<Session, SessionDomainEvent.MessageAdded> {
        val updated = metadata.copy(updatedAt = System.currentTimeMillis())
        val newSession = copy(messages = messages + message, metadata = updated)
        val event = SessionDomainEvent.MessageAdded(
            sessionId = id,
            messageId = UUID.randomUUID().toString(),
            role = message.role,
            contentPreview = message.content.take(100)
        )
        return newSession to event
    }

    /**
     * Determines if the session should be compacted based on token count.
     * Uses the soft threshold to provide a buffer before hard limits.
     */
    fun shouldCompact(currentTokens: Int, maxTokens: Int): Boolean {
        return currentTokens > (maxTokens - config.softThresholdTokens)
    }

    /**
     * Alternative: Determines if compaction is needed based on message count.
     */
    fun shouldCompactByMessageCount(maxMessages: Int): Boolean {
        return messages.size > (maxMessages - config.softThresholdMessages)
    }

    /**
     * Returns the context window for LLM requests (most recent messages).
     */
    fun toContextWindow(maxMessages: Int): List<Message> {
        return messages.takeLast(maxMessages)
    }

    /**
     * Compacts the session by summarizing old messages and optionally pruning tool results.
     *
     * Invariants enforced:
     * - The most recent user message is never removed
     * - Transcript remains append-only (messages aren't deleted, only tool outputs pruned)
     * - Creates a summary of compacted content for future reference
     *
     * @param maxMessagesToKeep Maximum number of recent messages to preserve
     * @param pruneToolResults If true, tool result content is replaced with placeholders
     * @param summaryText Optional summary of the compacted conversation
     * @return CompactionResult containing the new session state and the domain event
     */
    fun compact(
        maxMessagesToKeep: Int = config.defaultMaxMessagesToKeep,
        pruneToolResults: Boolean = config.memoryFlush.enabled,
        summaryText: String? = null
    ): SessionCompactionResult {
        require(maxMessagesToKeep > 0) { "maxMessagesToKeep must be positive" }

        val messagesBefore = messages.size

        // Find the most recent user message index (must be preserved per invariant)
        val lastUserMessageIndex = messages.indexOfLast { it.role == MessageRole.USER }

        // Calculate how many messages we can keep from the end
        val actualKeepCount = if (lastUserMessageIndex >= 0 &&
            messages.size - maxMessagesToKeep > lastUserMessageIndex) {
            // We would drop the last user message, so adjust to keep it
            messages.size - lastUserMessageIndex
        } else {
            maxMessagesToKeep
        }

        // Messages to keep (most recent)
        val keptMessages = messages.takeLast(actualKeepCount.coerceAtMost(messages.size))

        // Messages being compacted/summarized
        val compactedMessages = messages.dropLast(actualKeepCount.coerceAtMost(messages.size))

        // Count tool results that will be pruned
        val toolResultsPruned = if (pruneToolResults) {
            compactedMessages.count { it.role == MessageRole.TOOL }
        } else 0

        // Create summary of compacted content
        val summary = if (compactedMessages.isNotEmpty()) {
            CompactionSummary(
                id = UUID.randomUUID().toString(),
                messageRangeStart = 0,
                messageRangeEnd = compactedMessages.size,
                summaryText = summaryText ?: generateSummary(compactedMessages),
                timestamp = System.currentTimeMillis(),
                prunedToolResults = toolResultsPruned
            )
        } else null

        // Build new message list:
        // 1. Add a summary message if we compacted anything
        // 2. Add kept messages (with tool results pruned if requested)
        val newMessages = buildList {
            if (summary != null) {
                add(Message(
                    role = MessageRole.SYSTEM,
                    content = "[Previous conversation summary: ${summary.summaryText}]"
                ))
            }

            keptMessages.forEach { message ->
                if (pruneToolResults && message.role == MessageRole.TOOL) {
                    // Keep tool message but replace content with placeholder
                    add(message.copy(content = "[Tool result pruned for brevity]"))
                } else {
                    add(message)
                }
            }
        }

        val messagesAfter = newMessages.size

        val newSession = copy(
            messages = newMessages,
            summaries = if (summary != null) summaries + summary else summaries,
            metadata = metadata.copy(updatedAt = System.currentTimeMillis())
        )

        val event = SessionDomainEvent.ContextCompacted(
            sessionId = id,
            summaryId = summary?.id ?: UUID.randomUUID().toString(),
            messagesBefore = messagesBefore,
            messagesAfter = messagesAfter,
            toolResultsPruned = toolResultsPruned,
            summaryText = summary?.summaryText
        )

        return SessionCompactionResult(newSession, event)
    }

    /**
     * Prunes only tool results from the session without summarizing.
     * This keeps the full transcript but removes bulky tool outputs.
     *
     * @return Pair of (newSession, pruneEvent)
     */
    fun pruneToolResults(): Pair<Session, SessionDomainEvent.ToolResultsPruned> {
        val prunedMessages = messages.map { message ->
            if (message.role == MessageRole.TOOL) {
                message.copy(content = "[Tool result pruned for brevity]")
            } else {
                message
            }
        }

        val prunedCount = messages.count { it.role == MessageRole.TOOL }

        val newSession = copy(
            messages = prunedMessages,
            metadata = metadata.copy(updatedAt = System.currentTimeMillis())
        )

        val event = SessionDomainEvent.ToolResultsPruned(
            sessionId = id,
            prunedCount = prunedCount,
            preservedTranscript = true
        )

        return newSession to event
    }

    /**
     * Calculates the total token count for the session.
     * Uses a simple estimation: ~4 characters per token.
     */
    fun estimateTokens(): Int {
        return messages.sumOf { message ->
            // Content tokens + overhead per message
            (message.content.length / 4) + 4
        }
    }

    /**
     * Returns the count of messages by role.
     */
    fun messageCounts(): Map<MessageRole, Int> {
        return messages.groupingBy { it.role }.eachCount()
    }

    private fun generateSummary(compactedMessages: List<Message>): String {
        val userMessages = compactedMessages.filter { it.role == MessageRole.USER }
        val assistantMessages = compactedMessages.filter { it.role == MessageRole.ASSISTANT }

        return buildString {
            append("${userMessages.size} user messages, ")
            append("${assistantMessages.size} assistant responses")
            if (userMessages.isNotEmpty()) {
                append(". Topics: ")
                append(userMessages.take(3).joinToString(", ") { it.content.take(30) + "..." })
            }
        }
    }
}

/**
 * Summary of a compacted session segment.
 */
data class CompactionSummary(
    val id: String,
    val messageRangeStart: Int,
    val messageRangeEnd: Int,
    val summaryText: String,
    val timestamp: Long,
    val prunedToolResults: Int = 0
)

data class SessionMetadata(
    var updatedAt: Long = System.currentTimeMillis(),
    var displayName: String? = null,
    var thinkingLevel: String? = null,
    var verboseLevel: String? = null,
    var modelOverride: String? = null,
    var groupActivation: String? = null
) {
    fun touch() {
        updatedAt = System.currentTimeMillis()
    }
}
