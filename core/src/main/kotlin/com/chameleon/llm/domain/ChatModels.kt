package com.chameleon.llm.domain

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    val maxTokens: Int? = null,
    val toolSchemasJson: String? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)

sealed interface ChatCompletionEvent {
    data class AssistantDelta(val text: String, val reasoning: String? = null) : ChatCompletionEvent
    data class ToolCallDelta(val id: String, val name: String, val argumentsJson: String) : ChatCompletionEvent
    data class Completed(val finishReason: String? = null) : ChatCompletionEvent
}
