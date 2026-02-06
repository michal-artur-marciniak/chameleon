package com.chameleon.llm.port

import com.chameleon.llm.ChatCompletionEvent
import com.chameleon.llm.ChatCompletionRequest
import kotlinx.coroutines.flow.Flow

interface LlmProviderPort {
    suspend fun complete(request: ChatCompletionRequest): String
    fun stream(request: ChatCompletionRequest): Flow<ChatCompletionEvent>
}
