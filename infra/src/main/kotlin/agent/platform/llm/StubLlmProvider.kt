package agent.platform.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StubLlmProvider : LlmProviderPort {
    override suspend fun complete(request: ChatCompletionRequest): String {
        return "stub"
    }

    override fun stream(request: ChatCompletionRequest): Flow<ChatCompletionEvent> = flow {
        emit(ChatCompletionEvent.AssistantDelta("[stub] Agent loop wired. Reply: "))
        emit(ChatCompletionEvent.AssistantDelta(request.messages.lastOrNull()?.content ?: ""))
        emit(ChatCompletionEvent.Completed("stop"))
    }
}
