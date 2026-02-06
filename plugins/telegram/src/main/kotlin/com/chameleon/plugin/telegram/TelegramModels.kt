package com.chameleon.plugin.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramUpdateResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate> = emptyList(),
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)

@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramMessage? = null
)

@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val text: String? = null,
    val chat: TelegramChat,
    val from: TelegramUser? = null
)

@Serializable
data class TelegramChat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null
)

@Serializable
data class TelegramUser(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null
)

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    val text: String
)

@Serializable
data class SendMessageResponse(
    val ok: Boolean,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)

@Serializable
data class GetMeResponse(
    val ok: Boolean,
    val result: TelegramBotUser? = null,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)

@Serializable
data class TelegramBotUser(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null
)

@Serializable
data class TelegramSimpleResponse(
    val ok: Boolean,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)
