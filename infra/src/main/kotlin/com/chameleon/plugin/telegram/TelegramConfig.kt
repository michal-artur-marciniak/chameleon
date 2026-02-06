package com.chameleon.plugin.telegram

import kotlinx.serialization.Serializable

@Serializable
data class TelegramConfig(
    val enabled: Boolean = false,
    val token: String? = null,
    val mode: String = "polling",
    val requireMention: Boolean = true,
    val allowedUsers: List<String> = emptyList(),
    val allowedGroups: List<String> = emptyList()
)
