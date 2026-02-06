package agent.platform.session.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null
)

@Serializable
enum class MessageRole {
    @SerialName("system")
    SYSTEM,
    @SerialName("user")
    USER,
    @SerialName("assistant")
    ASSISTANT,
    @SerialName("tool")
    TOOL
}

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)
