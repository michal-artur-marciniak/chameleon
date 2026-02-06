package com.chameleon.channel.domain

import kotlinx.serialization.Serializable

@Serializable
data class ChannelsConfig(
    val telegram: TelegramConfig = TelegramConfig()
)

@Serializable
data class TelegramConfig(
    val enabled: Boolean = false,
    val token: String? = null,
    val mode: String = "polling",
    val requireMention: Boolean = true,
    val allowedUsers: List<String> = emptyList(),
    val allowedGroups: List<String> = emptyList()
)
