package agent.platform.llm.ports

import agent.platform.llm.ChatCompletionEvent
import agent.platform.llm.ChatCompletionRequest
import kotlinx.coroutines.flow.Flow

interface LlmProviderPort {
    suspend fun complete(request: ChatCompletionRequest): String
    fun stream(request: ChatCompletionRequest): Flow<ChatCompletionEvent>
}
