package com.chameleon.llm.port

import com.chameleon.llm.domain.ChatCompletionEvent
import com.chameleon.llm.domain.ChatCompletionRequest
import kotlinx.coroutines.flow.Flow

interface LlmProviderPort {
    suspend fun complete(request: ChatCompletionRequest): String
    fun stream(request: ChatCompletionRequest): Flow<ChatCompletionEvent>
}
