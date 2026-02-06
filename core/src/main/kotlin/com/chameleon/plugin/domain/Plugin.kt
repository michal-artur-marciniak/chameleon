package com.chameleon.plugin.domain

import com.chameleon.sdk.ChannelPort

/**
 * Plugin entity - represents a loaded plugin instance
 */
data class Plugin(
    val id: PluginId,
    val version: PluginVersion,
    val type: PluginType,
    val capabilities: Set<PluginCapability>,
    val source: PluginSource,
    val instance: ChannelPort,
    var status: PluginStatus = PluginStatus.DISABLED
) {
    val isEnabled: Boolean
        get() = status == PluginStatus.ENABLED
    
    val isOfficial: Boolean
        get() = source == PluginSource.OFFICIAL
    
    fun enable() {
        status = PluginStatus.ENABLED
    }
    
    fun disable() {
        status = PluginStatus.DISABLED
    }
}
