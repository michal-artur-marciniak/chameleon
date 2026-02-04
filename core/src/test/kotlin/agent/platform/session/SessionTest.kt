package agent.platform.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SessionTest {

    private fun createTestSession(messages: List<Message> = emptyList()): Session {
        return Session(
            id = SessionId.generate(),
            key = SessionKey.parse("agent:test:telegram:dm:123"),
            messages = messages,
            config = CompactionConfig(
                reserveTokensFloor = 20000,
                softThresholdTokens = 4000,
                softThresholdMessages = 5,
                defaultMaxMessagesToKeep = 10
            )
        )
    }

    @Test
    fun `shouldCompact returns true when tokens exceed threshold`() {
        val session = createTestSession()
        val maxTokens = 10000
        val currentTokens = 7000 // Above threshold (10000 - 4000 = 6000)

        assertTrue(session.shouldCompact(currentTokens, maxTokens))
    }

    @Test
    fun `shouldCompact returns false when tokens are below threshold`() {
        val session = createTestSession()
        val maxTokens = 10000
        val currentTokens = 5000 // Below threshold (10000 - 4000 = 6000)

        assertFalse(session.shouldCompact(currentTokens, maxTokens))
    }

    @Test
    fun `shouldCompactByMessageCount returns true when messages exceed threshold`() {
        val messages = List(20) { index ->
            Message(role = MessageRole.USER, content = "Message $index")
        }
        val session = createTestSession(messages)

        assertTrue(session.shouldCompactByMessageCount(maxMessages = 15))
    }

    @Test
    fun `compact keeps most recent messages`() {
        val messages = List(20) { index ->
            Message(role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT, content = "Message $index")
        }
        val session = createTestSession(messages)

        val result = session.compact(maxMessagesToKeep = 5)

        // 1 summary message + 5 kept messages = 6 total
        assertEquals(6, result.newSession.messages.size)
        assertEquals("Message 19", result.newSession.messages.last().content)
        // First message is the summary, second is the oldest kept message
        assertEquals(MessageRole.SYSTEM, result.newSession.messages.first().role)
        assertEquals("Message 15", result.newSession.messages[1].content)
    }

    @Test
    fun `compact preserves most recent user message`() {
        // Create messages where the last user message is near the boundary
        val messages = List(20) { index ->
            when {
                index == 15 -> Message(role = MessageRole.USER, content = "Important user message")
                index > 15 -> Message(role = MessageRole.ASSISTANT, content = "Assistant response $index")
                else -> Message(role = MessageRole.USER, content = "Older message $index")
            }
        }
        val session = createTestSession(messages)

        // Try to compact keeping only 2 messages (which would drop the user message at index 15)
        val result = session.compact(maxMessagesToKeep = 2)

        // Should still have the user message at index 15
        val hasImportantMessage = result.newSession.messages.any { it.content == "Important user message" }
        assertTrue(hasImportantMessage, "Most recent user message should be preserved")
    }

    @Test
    fun `compact emits ContextCompacted event`() {
        val messages = List(20) { index ->
            Message(role = MessageRole.USER, content = "Message $index")
        }
        val session = createTestSession(messages)

        val result = session.compact(maxMessagesToKeep = 5)

        assertEquals(session.id, result.event.sessionId)
        assertEquals(20, result.event.messagesBefore)
        // 1 summary + 5 kept = 6 messages after
        assertEquals(6, result.event.messagesAfter)
        assertNotNull(result.event.summaryId)
    }

    @Test
    fun `compact generates summary when no summary text provided`() {
        val messages = List(10) { index ->
            when {
                index % 2 == 0 -> Message(role = MessageRole.USER, content = "User question $index")
                else -> Message(role = MessageRole.ASSISTANT, content = "Assistant answer $index")
            }
        }
        val session = createTestSession(messages)

        val result = session.compact(maxMessagesToKeep = 5)

        assertNotNull(result.event.summaryText)
        assertTrue(result.event.summaryText!!.contains("user messages"))
    }

    @Test
    fun `compact uses provided summary text`() {
        val messages = List(10) { index ->
            Message(role = MessageRole.USER, content = "Message $index")
        }
        val session = createTestSession(messages)
        val customSummary = "Custom conversation summary"

        val result = session.compact(maxMessagesToKeep = 5, summaryText = customSummary)

        assertEquals(customSummary, result.event.summaryText)
    }

    @Test
    fun `compact adds summary to session summaries list`() {
        val messages = List(10) { index ->
            Message(role = MessageRole.USER, content = "Message $index")
        }
        val session = createTestSession(messages)

        val result = session.compact(maxMessagesToKeep = 5)

        assertEquals(1, result.newSession.summaries.size)
        assertEquals(0, result.newSession.summaries[0].messageRangeStart)
        assertEquals(5, result.newSession.summaries[0].messageRangeEnd)
    }

    @Test
    fun `compact with tool pruning replaces tool content`() {
        val messages = listOf(
            Message(role = MessageRole.USER, content = "Run command"),
            Message(role = MessageRole.ASSISTANT, content = "I'll help"),
            Message(role = MessageRole.TOOL, content = "Very long tool output here...", toolCallId = "call-1"),
            Message(role = MessageRole.USER, content = "Thanks")
        )
        val session = createTestSession(messages)

        val result = session.compact(maxMessagesToKeep = 2, pruneToolResults = true)

        // Check that tool results in compacted portion are pruned
        val toolMessages = result.newSession.messages.filter { it.role == MessageRole.TOOL }
        toolMessages.forEach { message ->
            assertEquals("[Tool result pruned for brevity]", message.content)
        }
    }

    @Test
    fun `compact without tool pruning keeps tool content`() {
        val messages = listOf(
            Message(role = MessageRole.USER, content = "Run command"),
            Message(role = MessageRole.ASSISTANT, content = "I'll help"),
            Message(role = MessageRole.TOOL, content = "Important tool output", toolCallId = "call-1"),
            Message(role = MessageRole.USER, content = "Thanks")
        )
        val session = createTestSession(messages)

        val result = session.compact(maxMessagesToKeep = 2, pruneToolResults = false)

        val toolMessages = result.newSession.messages.filter { it.role == MessageRole.TOOL }
        toolMessages.forEach { message ->
            assertEquals("Important tool output", message.content)
        }
    }

    @Test
    fun `pruneToolResults replaces all tool content`() {
        val messages = listOf(
            Message(role = MessageRole.USER, content = "Command 1"),
            Message(role = MessageRole.TOOL, content = "Output 1", toolCallId = "call-1"),
            Message(role = MessageRole.USER, content = "Command 2"),
            Message(role = MessageRole.TOOL, content = "Output 2", toolCallId = "call-2")
        )
        val session = createTestSession(messages)

        val (newSession, event) = session.pruneToolResults()

        assertEquals(2, event.prunedCount)
        assertTrue(event.preservedTranscript)

        val toolMessages = newSession.messages.filter { it.role == MessageRole.TOOL }
        assertEquals(2, toolMessages.size)
        toolMessages.forEach { message ->
            assertEquals("[Tool result pruned for brevity]", message.content)
        }
    }

    @Test
    fun `pruneToolResults emits ToolResultsPruned event`() {
        val messages = listOf(
            Message(role = MessageRole.USER, content = "Command"),
            Message(role = MessageRole.TOOL, content = "Output", toolCallId = "call-1")
        )
        val session = createTestSession(messages)

        val (newSession, event) = session.pruneToolResults()

        assertEquals(session.id, event.sessionId)
        assertEquals(1, event.prunedCount)
        assertTrue(event.preservedTranscript)
    }

    @Test
    fun `withMessage adds message and emits event`() {
        val session = createTestSession()
        val message = Message(role = MessageRole.USER, content = "Hello")

        val (newSession, event) = session.withMessage(message)

        assertEquals(1, newSession.messages.size)
        assertEquals("Hello", newSession.messages[0].content)
        assertEquals(MessageRole.USER, event.role)
        assertEquals("Hello", event.contentPreview)
        assertEquals(session.id, event.sessionId)
    }

    @Test
    fun `toContextWindow returns last N messages`() {
        val messages = List(20) { index ->
            Message(role = MessageRole.USER, content = "Message $index")
        }
        val session = createTestSession(messages)

        val context = session.toContextWindow(5)

        assertEquals(5, context.size)
        assertEquals("Message 15", context[0].content)
        assertEquals("Message 19", context[4].content)
    }

    @Test
    fun `estimateTokens calculates approximate token count`() {
        val messages = listOf(
            Message(role = MessageRole.USER, content = "Hello"), // ~1 token + 4 overhead = 5
            Message(role = MessageRole.ASSISTANT, content = "World") // ~1 token + 4 overhead = 5
        )
        val session = createTestSession(messages)

        val tokens = session.estimateTokens()

        assertEquals(10, tokens) // 5 + 5
    }

    @Test
    fun `messageCounts returns correct counts by role`() {
        val messages = listOf(
            Message(role = MessageRole.USER, content = "1"),
            Message(role = MessageRole.USER, content = "2"),
            Message(role = MessageRole.ASSISTANT, content = "3"),
            Message(role = MessageRole.TOOL, content = "4")
        )
        val session = createTestSession(messages)

        val counts = session.messageCounts()

        assertEquals(2, counts[MessageRole.USER])
        assertEquals(1, counts[MessageRole.ASSISTANT])
        assertEquals(1, counts[MessageRole.TOOL])
    }

    @Test
    fun `compact throws on invalid maxMessagesToKeep`() {
        val session = createTestSession()

        assertFailsWith<IllegalArgumentException> {
            session.compact(maxMessagesToKeep = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            session.compact(maxMessagesToKeep = -1)
        }
    }

    @Test
    fun `compact handles empty session gracefully`() {
        val session = createTestSession()

        val result = session.compact(maxMessagesToKeep = 10)

        assertEquals(0, result.newSession.messages.size)
        assertEquals(0, result.event.messagesBefore)
        assertEquals(0, result.event.messagesAfter)
    }

    @Test
    fun `compact adds system message with summary`() {
        val messages = List(20) { index ->
            Message(role = MessageRole.USER, content = "Message $index")
        }
        val session = createTestSession(messages)

        val result = session.compact(maxMessagesToKeep = 5)

        assertEquals(MessageRole.SYSTEM, result.newSession.messages[0].role)
        assertTrue(result.newSession.messages[0].content.startsWith("[Previous conversation summary:"))
    }

    @Test
    fun `multiple compactions accumulate summaries`() {
        val messages = List(30) { index ->
            Message(role = MessageRole.USER, content = "Message $index")
        }
        val session = createTestSession(messages)

        val result1 = session.compact(maxMessagesToKeep = 20)
        val result2 = result1.newSession.compact(maxMessagesToKeep = 10)

        assertEquals(2, result2.newSession.summaries.size)
    }

    @Test
    fun `compact counts tool results correctly`() {
        val messages = listOf(
            Message(role = MessageRole.USER, content = "1"),
            Message(role = MessageRole.TOOL, content = "output 1", toolCallId = "1"),
            Message(role = MessageRole.USER, content = "2"),
            Message(role = MessageRole.TOOL, content = "output 2", toolCallId = "2"),
            Message(role = MessageRole.USER, content = "3"),
            Message(role = MessageRole.ASSISTANT, content = "response")
        )
        val session = createTestSession(messages)

        val result = session.compact(maxMessagesToKeep = 2, pruneToolResults = true)

        assertEquals(2, result.event.toolResultsPruned)
    }
}