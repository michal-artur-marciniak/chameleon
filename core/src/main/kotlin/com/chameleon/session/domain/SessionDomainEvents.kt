package com.chameleon.session.domain

import com.chameleon.session.domain.SessionId

import java.time.Instant
import java.util.UUID

/**
 * Domain events emitted by the Session aggregate.
 * These represent significant lifecycle events for sessions.
 */
sealed interface SessionDomainEvent {
    val sessionId: SessionId
    val timestamp: Instant

    /**
     * Emitted when a session's context is compacted.
     * Tool results may be pruned but the transcript remains append-only.
     */
    data class ContextCompacted(
        override val sessionId: SessionId,
        val summaryId: String = UUID.randomUUID().toString(),
        val messagesBefore: Int,
        val messagesAfter: Int,
        val toolResultsPruned: Int,
        val summaryText: String? = null,
        override val timestamp: Instant = Instant.now()
    ) : SessionDomainEvent

    /**
     * Emitted when a message is added to the session.
     */
    data class MessageAdded(
        override val sessionId: SessionId,
        val messageId: String = UUID.randomUUID().toString(),
        val role: MessageRole,
        val contentPreview: String,
        override val timestamp: Instant = Instant.now()
    ) : SessionDomainEvent

    /**
     * Emitted when tool results are pruned from the session.
     * The transcript remains but tool outputs are removed to save space.
     */
    data class ToolResultsPruned(
        override val sessionId: SessionId,
        val prunedCount: Int,
        val preservedTranscript: Boolean = true,
        override val timestamp: Instant = Instant.now()
    ) : SessionDomainEvent
}
