package agent.platform.session

data class Session(
    val id: SessionId,
    val key: SessionKey,
    val messages: List<Message> = emptyList(),
    val config: CompactionConfig = CompactionConfig(),
    val metadata: SessionMetadata = SessionMetadata()
) {
    fun withMessage(message: Message): Session {
        val updated = metadata.copy(updatedAt = System.currentTimeMillis())
        return copy(messages = messages + message, metadata = updated)
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
