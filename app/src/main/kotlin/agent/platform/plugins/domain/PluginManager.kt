package agent.platform.plugins.domain

import agent.sdk.ChannelPort
import agent.sdk.InboundMessage
import agent.sdk.OutboundMessage
import agent.platform.config.PlatformConfig
import java.nio.file.Path

/**
 * PluginManager - Aggregate Root for the Plugin Context
 * 
 * Manages the lifecycle and registry of all plugins (official + external)
 */
class PluginManager(
    private val config: PlatformConfig,
    private val pluginsDir: Path
) {
    private val plugins = mutableMapOf<PluginId, Plugin>()
    private val eventListeners = mutableListOf<PluginEventListener>()
    
    // Registry of official plugin factories
    private val officialRegistry = OfficialPluginRegistry()
    
    // External plugin repository
    private val externalRepository = FileSystemPluginRepository(pluginsDir)
    
    fun addEventListener(listener: PluginEventListener) {
        eventListeners.add(listener)
    }
    
    /**
     * Discover and load all plugins (official + external)
     */
    fun loadAll(): List<Plugin> {
        val loadedPlugins = mutableListOf<Plugin>()
        
        // Load official plugins first
        loadedPlugins.addAll(loadOfficialPlugins())
        
        // Load external plugins
        loadedPlugins.addAll(loadExternalPlugins())
        
        return loadedPlugins
    }
    
    private fun loadOfficialPlugins(): List<Plugin> {
        val discovered = officialRegistry.discover(config)
        return discovered.mapNotNull { factory ->
            loadPlugin(factory)
        }
    }
    
    private fun loadExternalPlugins(): List<Plugin> {
        val discovered = externalRepository.discover()
        return discovered.mapNotNull { manifest ->
            // Skip if official plugin with same ID exists
            if (plugins.containsKey(PluginId(manifest.id))) {
                emit(PluginSkipped(manifest.id, "Official plugin with same ID exists"))
                return@mapNotNull null
            }
            loadExternalPlugin(manifest)
        }
    }
    
    private fun loadPlugin(factory: PluginFactory): Plugin? {
        return try {
            val instance = factory.create(config)
                ?: return null.also { 
                    emit(PluginSkipped(factory.id.value, "Factory returned null"))
                }
            
            val plugin = Plugin(
                id = factory.id,
                version = factory.version,
                type = factory.type,
                capabilities = factory.capabilities,
                source = PluginSource.OFFICIAL,
                instance = instance,
                status = PluginStatus.ENABLED
            )
            
            plugins[plugin.id] = plugin
            emit(PluginLoaded(plugin.id.value, plugin.version.toString(), plugin.source))
            plugin
            
        } catch (e: Exception) {
            emit(PluginError(factory.id.value, "Failed to load: ${e.message}"))
            null
        }
    }
    
    private fun loadExternalPlugin(manifest: agent.sdk.PluginManifest): Plugin? {
        return try {
            val instance = externalRepository.load(manifest, config)
                ?: return null.also {
                    emit(PluginSkipped(manifest.id, "Repository failed to load plugin"))
                }
            
            val pluginType = PluginType.fromString(manifest.type)
            val capabilities = when (pluginType) {
                PluginType.CHANNEL -> setOf(PluginCapability.RECEIVE_MESSAGES, PluginCapability.SEND_MESSAGES)
                else -> emptySet()
            }
            
            val plugin = Plugin(
                id = PluginId(manifest.id),
                version = PluginVersion.parse(manifest.version),
                type = pluginType,
                capabilities = capabilities,
                source = PluginSource.EXTERNAL,
                instance = instance,
                status = PluginStatus.ENABLED
            )
            
            plugins[plugin.id] = plugin
            emit(PluginLoaded(plugin.id.value, plugin.version.toString(), plugin.source))
            plugin
            
        } catch (e: Exception) {
            emit(PluginError(manifest.id, "Failed to load external plugin: ${e.message}"))
            null
        }
    }
    
    fun get(id: PluginId): Plugin? = plugins[id]
    
    fun getAll(): List<Plugin> = plugins.values.toList()
    
    fun getEnabled(): List<Plugin> = plugins.values.filter { it.isEnabled }
    
    fun enable(id: PluginId): Boolean {
        val plugin = plugins[id] ?: return false
        plugin.enable()
        emit(PluginEnabled(id.value))
        return true
    }
    
    fun disable(id: PluginId): Boolean {
        val plugin = plugins[id] ?: return false
        plugin.disable()
        emit(PluginDisabled(id.value))
        return true
    }
    
    fun reload(id: PluginId): Plugin? {
        val existing = plugins[id] ?: return null
        
        emit(PluginReloaded(id.value))
        
        return if (existing.isOfficial) {
            plugins.remove(id)
            officialRegistry.getFactory(id)?.let { loadPlugin(it) }
        } else {
            plugins.remove(id)
            externalRepository.findManifest(id)?.let { loadExternalPlugin(it) }
        }
    }
    
    private fun emit(event: PluginEvent) {
        eventListeners.forEach { it.onEvent(event) }
    }
}

interface PluginEventListener {
    fun onEvent(event: PluginEvent)
}
