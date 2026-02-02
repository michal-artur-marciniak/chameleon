package agent.platform.plugins.domain

import agent.sdk.ChannelPort
import agent.sdk.PluginManifest
import java.nio.file.Path

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
    fun load(manifest: PluginManifest, config: Any?): ChannelPort?
    
    /**
     * Find manifest by plugin ID
     */
    fun findManifest(id: PluginId): PluginManifest?
}
