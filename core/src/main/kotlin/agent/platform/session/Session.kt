package agent.platform.session

class Session(
    val id: SessionId,
    val key: SessionKey,
    messages: List<Message> = emptyList(),
    val config: CompactionConfig = CompactionConfig(),
    val metadata: SessionMetadata = SessionMetadata()
) {
    private val storedMessages = messages.toMutableList()
    val messages: List<Message> get() = storedMessages.toList()

    fun addMessage(message: Message) {
        storedMessages.add(message)
        metadata.touch()
    }

    fun shouldCompact(currentTokens: Int, maxTokens: Int): Boolean {
        return currentTokens > (maxTokens - config.softThresholdTokens)
    }

    fun toContextWindow(maxMessages: Int): List<Message> {
        return messages.takeLast(maxMessages)
    }
}

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
