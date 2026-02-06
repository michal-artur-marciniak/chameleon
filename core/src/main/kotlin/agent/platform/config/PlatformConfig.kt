package agent.platform.config

import agent.platform.agent.AgentsConfig
import agent.platform.channel.ChannelsConfig
import agent.platform.channel.GatewayConfig
import agent.platform.llm.ModelsConfig
import agent.platform.session.MessagesConfig
import agent.platform.tool.domain.ToolsConfig
import kotlinx.serialization.Serializable

@Serializable
data class PlatformConfig(
    val models: ModelsConfig = ModelsConfig(),
    val agents: AgentsConfig = AgentsConfig(),
    val messages: MessagesConfig = MessagesConfig(),
    val tools: ToolsConfig = ToolsConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val channels: ChannelsConfig = ChannelsConfig(),
    val gateway: GatewayConfig = GatewayConfig()
)
