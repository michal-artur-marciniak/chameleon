package agent.platform.plugins.domain

import agent.platform.config.PlatformConfig
import agent.sdk.ChannelPort

interface PluginFactory {
    val id: PluginId
    val version: PluginVersion
    val type: PluginType
    val capabilities: Set<PluginCapability>

    fun isEnabled(config: PlatformConfig): Boolean
    fun create(config: PlatformConfig): ChannelPort?
}

interface PluginFactoryRegistry {
    fun discover(config: PlatformConfig): List<PluginFactory>
    fun getFactory(id: PluginId): PluginFactory?
}
