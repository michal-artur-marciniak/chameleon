package com.chameleon.plugin.port

import com.chameleon.config.domain.PlatformConfig
import com.chameleon.plugin.domain.PluginId
import com.chameleon.sdk.ChannelPort
import com.chameleon.sdk.PluginManifest

/**
 * Repository interface for plugin discovery and loading
 */
interface PluginRepository {
    /**
     * Discover available plugins
     */
    fun discover(): List<PluginManifest>
    
    /**
     * Load a specific plugin by manifest
     */
    fun load(manifest: PluginManifest, config: PlatformConfig): ChannelPort?
    
    /**
     * Find manifest by plugin ID
     */
    fun findManifest(id: PluginId): PluginManifest?
}
