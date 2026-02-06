package com.chameleon.llm

import com.chameleon.llm.domain.ChatCompletionEvent
import com.chameleon.llm.domain.ChatCompletionRequest
import com.chameleon.llm.port.LlmProviderPort

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenAI-compatible LLM provider implementation.
 *
 * Supports any API that implements the OpenAI chat completions protocol.
 * Uses Ktor HTTP client for async requests.
 */
class OpenAiCompatProvider(
    private val baseUrl: String,
    private val apiKey: String?,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LlmProviderPort {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(this@OpenAiCompatProvider.json) }
    }

    override suspend fun complete(request: ChatCompletionRequest): String {
        val response = client.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            headerIfPresent("Authorization", apiKey?.let { "Bearer $it" })
            this@OpenAiCompatProvider.extraHeaders.forEach { (key, value) ->
                header(key, value)
            }
            setBody(
                ChatCompletionPayload(
                    model = request.model,
                    messages = request.messages.map { ChatMessagePayload(it.role, it.content) },
                    temperature = request.temperature,
                    maxTokens = request.maxTokens
                )
            )
        }.body<ChatCompletionResponse>()

        val content = response.choices.firstOrNull()?.message?.content
        return content ?: ""
    }

    override fun stream(request: ChatCompletionRequest): Flow<ChatCompletionEvent> = flow {
        val content = complete(request)
        if (content.isNotBlank()) {
            emit(ChatCompletionEvent.AssistantDelta(content))
        }
        emit(ChatCompletionEvent.Completed("stop"))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.headerIfPresent(
        name: String,
        value: String?
    ) {
        if (!value.isNullOrBlank()) {
            header(name, value)
        }
    }
}

@Serializable
private data class ChatCompletionPayload(
    val model: String,
    val messages: List<ChatMessagePayload>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null
)

@Serializable
private data class ChatMessagePayload(
    val role: String,
    val content: String
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<ChatCompletionChoice> = emptyList()
)

@Serializable
private data class ChatCompletionChoice(
    val message: ChatMessagePayload? = null
)
