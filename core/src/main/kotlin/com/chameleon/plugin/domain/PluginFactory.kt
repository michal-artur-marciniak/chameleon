package com.chameleon.plugin.domain

import com.chameleon.config.domain.PlatformConfig
import com.chameleon.sdk.ChannelPort

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
