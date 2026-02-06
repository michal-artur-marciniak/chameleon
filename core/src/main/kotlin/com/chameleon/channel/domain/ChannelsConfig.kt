package com.chameleon.channel.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChannelsConfig(
    val entries: Map<String, JsonObject> = emptyMap()
) {
    fun get(id: String): JsonObject? = entries[id]
}
