package com.chameleon.config

import com.chameleon.agent.AgentsConfig
import com.chameleon.channel.ChannelsConfig
import com.chameleon.channel.GatewayConfig
import com.chameleon.llm.ModelsConfig
import com.chameleon.session.MessagesConfig
import com.chameleon.tool.domain.ToolsConfig
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
