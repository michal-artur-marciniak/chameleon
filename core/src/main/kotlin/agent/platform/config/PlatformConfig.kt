package agent.platform.config

import kotlinx.serialization.Serializable

@Serializable
data class PlatformConfig(
    val agent: AgentConfig = AgentConfig(),
    val gateway: GatewayConfig = GatewayConfig(),
    val channels: ChannelsConfig = ChannelsConfig()
)

@Serializable
data class GatewayConfig(
    val host: String = "0.0.0.0",
    val port: Int = 18789
)

@Serializable
data class AgentConfig(
    val id: String = "main",
    val workspace: String = "workspace",
    val extensionsDir: String = "extensions"
)

@Serializable
data class ChannelsConfig(
    val telegram: TelegramConfig = TelegramConfig()
)

@Serializable
data class TelegramConfig(
    val enabled: Boolean = false,
    val token: String? = null,
    val mode: String = "polling"
)
