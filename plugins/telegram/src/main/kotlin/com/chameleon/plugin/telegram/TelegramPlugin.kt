package com.chameleon.plugin.telegram

import com.chameleon.sdk.ChannelPort
import com.chameleon.sdk.InboundMessage
import com.chameleon.sdk.OutboundMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

class TelegramPlugin(
    private val token: String,
    private val requireMentionInGroups: Boolean = true
) : ChannelPort {
    private val logger = org.slf4j.LoggerFactory.getLogger(TelegramPlugin::class.java)
    override val id: String = "telegram"

    private val baseUrl = "https://api.telegram.org/bot$token"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 70_000
        }
        install(ContentNegotiation) { json(this@TelegramPlugin.json) }
    }

    private var running = true
    private var offset: Long = 0
    private var botUsername: String? = null

    override suspend fun start(handler: suspend (InboundMessage) -> Unit) {
        disableWebhook()
        botUsername = fetchBotUsername()
        if (botUsername.isNullOrBlank()) {
            println("[telegram] bot username not available; mention checks may fail")
        }
        while (running && client.isActive) {
            val updates = withTimeoutOrNull(65_000) { pollUpdates() } ?: emptyList()
            updates.forEach { update ->
                offset = maxOf(offset, update.updateId + 1)
                val message = update.message ?: return@forEach
                val text = message.text ?: return@forEach
                val from = message.from ?: return@forEach
                val isGroup = message.chat.type != "private"
                val isMentioned = !isGroup || isMentioned(text)

                if (isGroup && requireMentionInGroups && !isMentioned) return@forEach

                handler(
                    InboundMessage(
                        channelId = id,
                        chatId = message.chat.id.toString(),
                        userId = from.id.toString(),
                        text = text,
                        isGroup = isGroup,
                        isMentioned = isMentioned
                    )
                )
            }
            delay(250)
        }
    }

    override suspend fun send(message: OutboundMessage): Result<Unit> {
        return runCatching {
            client.post("$baseUrl/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest(chatId = message.chatId.toLong(), text = message.text))
            }.body<SendMessageResponse>()
        }.map {
            if (!it.ok) {
                error("sendMessage failed: ${it.errorCode} ${it.description}")
            }
            Unit
        }
    }

    override suspend fun stop() {
        running = false
        client.close()
    }

    private suspend fun pollUpdates(): List<TelegramUpdate> {
        val response: TelegramUpdateResponse = client.get("$baseUrl/getUpdates") {
            url { parameters.append("timeout", "60") }
            if (offset > 0) {
                url { parameters.append("offset", offset.toString()) }
            }
        }.body()
        if (!response.ok) {
            logger.warn("[telegram] getUpdates failed: {} {}", response.errorCode, response.description)
            return emptyList()
        }
        return response.result
    }

    private suspend fun fetchBotUsername(): String? {
        val response: GetMeResponse = client.get("$baseUrl/getMe").body()
        if (!response.ok) {
            logger.warn("[telegram] getMe failed: {} {}", response.errorCode, response.description)
            return null
        }
        return response.result?.username
    }

    private suspend fun disableWebhook() {
        val response: TelegramSimpleResponse = client.post("$baseUrl/deleteWebhook") {
            url { parameters.append("drop_pending_updates", "true") }
        }.body()
        if (!response.ok) {
            logger.warn("[telegram] deleteWebhook failed: {} {}", response.errorCode, response.description)
        }
    }

    private fun isMentioned(text: String): Boolean {
        val name = botUsername ?: return false
        return text.contains("@$name", ignoreCase = true)
    }
}
