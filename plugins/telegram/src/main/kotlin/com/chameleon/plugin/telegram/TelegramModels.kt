package com.chameleon.plugin.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response from getUpdates Telegram Bot API call */
@Serializable
data class TelegramUpdateResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate> = emptyList(),
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)

/** Represents a single update from Telegram (message, callback, etc.) */
@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramMessage? = null
)

/** Telegram message entity with sender and chat context */
@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val text: String? = null,
    val chat: TelegramChat,
    val from: TelegramUser? = null
)

/** Telegram chat (private, group, supergroup, or channel) */
@Serializable
data class TelegramChat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null
)

/** Telegram user entity */
@Serializable
data class TelegramUser(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null
)

/** Request body for sendMessage API call */
@Serializable
data class SendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    val text: String
)

/** Response from sendMessage API call */
@Serializable
data class SendMessageResponse(
    val ok: Boolean,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)

/** Response from getMe API call (bot info) */
@Serializable
data class GetMeResponse(
    val ok: Boolean,
    val result: TelegramBotUser? = null,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)

/** Telegram bot user info */
@Serializable
data class TelegramBotUser(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null
)

/** Generic Telegram API response for simple operations */
@Serializable
data class TelegramSimpleResponse(
    val ok: Boolean,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)
