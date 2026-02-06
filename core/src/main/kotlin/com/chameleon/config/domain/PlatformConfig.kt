package com.chameleon.config.domain

import com.chameleon.agent.AgentsConfig
import com.chameleon.channel.domain.ChannelsConfig
import com.chameleon.channel.domain.GatewayConfig
import com.chameleon.llm.domain.ModelsConfig
import com.chameleon.session.domain.MessagesConfig
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
