package com.chameleon.application

import com.chameleon.agent.ContextBundle
import com.chameleon.llm.ChatCompletionRequest
import com.chameleon.llm.ChatMessage
import com.chameleon.session.domain.MessageRole

/**
 * Plan for an LLM completion request including token estimation.
 *
 * @property request The chat completion request ready to send to LLM provider
 * @property promptTokens Estimated number of tokens in the prompt (for observability)
 */
data class LlmRequestPlan(
    val request: ChatCompletionRequest,
    val promptTokens: Int
)

/**
 * Builds LLM completion requests from agent context.
 *
 * Handles conversion of domain objects to LLM provider format:
 * - Merges memory context into system prompt
 * - Converts domain messages to LLM format
 * - Estimates prompt tokens for observability
 */
class LlmRequestBuilder {

    /**
     * Builds a chat completion request from context.
     *
     * @param context The agent context containing system prompt, messages, and tool schemas
     * @param modelId The target model identifier
     * @param memoryContext Optional memory context to inject into system prompt
     * @return Complete request plan with estimated token count
     */
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

    /**
     * Converts domain message role to LLM provider role string.
     *
     * @param role The domain message role
     * @return LLM provider role string
     */
    private fun toRole(role: MessageRole): String {
        return when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.TOOL -> "tool"
        }
    }

    /**
     * Estimates token count using a simple character-based heuristic.
     *
     * Approximation: 1 token ≈ 4 characters
     *
     * @param text The text to estimate tokens for
     * @return Estimated token count
     */
    private fun estimateTokens(text: String): Int {
        return text.length / 4
    }
}
