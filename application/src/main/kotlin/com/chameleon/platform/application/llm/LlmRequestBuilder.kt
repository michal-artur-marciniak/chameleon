package com.chameleon.application.llm

import com.chameleon.agent.ContextBundle
import com.chameleon.llm.ChatCompletionRequest
import com.chameleon.llm.ChatMessage
import com.chameleon.session.domain.MessageRole

data class LlmRequestPlan(
    val request: ChatCompletionRequest,
    val promptTokens: Int
)

class LlmRequestBuilder {
    fun build(
        context: ContextBundle,
        modelId: String,
        memoryContext: String? = null
    ): LlmRequestPlan {
        val systemPrompt = if (memoryContext.isNullOrBlank()) {
            context.systemPrompt
        } else {
            context.systemPrompt + "\n\n" + memoryContext
        }

        val messages = mutableListOf(ChatMessage("system", systemPrompt))
        context.messages.forEach { message ->
            messages.add(ChatMessage(toRole(message.role), message.content))
        }

        val request = ChatCompletionRequest(
            model = modelId,
            messages = messages,
            toolSchemasJson = context.toolSchemasJson
        )

        return LlmRequestPlan(
            request = request,
            promptTokens = estimateTokens(systemPrompt)
        )
    }

    private fun toRole(role: MessageRole): String {
        return when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.TOOL -> "tool"
        }
    }

    private fun estimateTokens(text: String): Int {
        return text.length / 4
    }
}
