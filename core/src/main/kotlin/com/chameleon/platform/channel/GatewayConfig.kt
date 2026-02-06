package com.chameleon.channel

import kotlinx.serialization.Serializable

@Serializable
data class GatewayConfig(
    val host: String = "0.0.0.0",
    val port: Int = 18789
)
