package agent.platform.plugins

import agent.platform.config.PlatformConfig
import agent.platform.persistence.SessionIndexStore
import agent.platform.plugins.domain.PluginEvent
import agent.platform.plugins.domain.PluginEventListener
import agent.platform.plugins.domain.PluginId
import agent.platform.plugins.domain.PluginInfo
import agent.platform.plugins.domain.PluginManager
import agent.platform.plugins.domain.PluginDiscovered
import agent.platform.plugins.domain.PluginLoaded
import agent.platform.plugins.domain.PluginEnabled
import agent.platform.plugins.domain.PluginDisabled
import agent.platform.plugins.domain.PluginReloaded
import agent.platform.plugins.domain.PluginError
import agent.platform.plugins.domain.PluginSkipped
import agent.sdk.ChannelPort
import agent.sdk.InboundMessage
import agent.sdk.OutboundMessage
import agent.platform.plugins.FileSystemPluginRepository
import agent.platform.plugins.OfficialPluginRegistry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Application service that orchestrates the Plugin domain
 * 
 * This is NOT the domain - it uses the PluginManager aggregate root
 * to handle plugin lifecycle and coordinates with other contexts.
 */
class PluginService(
    private val config: PlatformConfig,
    private val configPath: Path?
) {
    private val extensionsDir: Path = Paths.get(config.agent.extensionsDir)
    private val workspace = Paths.get(config.agent.workspace)
    private val indexStore = SessionIndexStore(workspace)
    
    private val pluginManager: PluginManager
    private val runningPlugins = mutableMapOf<PluginId, CoroutineScope>()
    
    init {
        val officialRegistry = OfficialPluginRegistry()
        val externalRepository = FileSystemPluginRepository(extensionsDir)
        pluginManager = PluginManager(config, officialRegistry, externalRepository)
        pluginManager.addEventListener(LoggingEventListener())
    }

    fun startAll() {
        // Load all plugins through the domain
        val plugins = pluginManager.loadAll()

        if (plugins.isEmpty()) {
            println("[app] no plugins loaded")
            return
        }

        // Start enabled plugins
        plugins.filter { it.isEnabled }.forEach { plugin ->
            startPlugin(plugin.id)
        }
    }
    
    fun stopAll() {
        runningPlugins.forEach { (id, scope) ->
            println("[${id.value}] stopping")
            // Cancel the scope
            // Note: ChannelPort.stop() should be called for clean shutdown
        }
        runningPlugins.clear()
    }
    
    fun enablePlugin(id: String): Boolean {
        return pluginManager.enable(PluginId(id))
    }
    
    fun disablePlugin(id: String): Boolean {
        return pluginManager.disable(PluginId(id))
    }
    
    fun reloadPlugin(id: String): Boolean {
        val pluginId = PluginId(id)
        val reloaded = pluginManager.reload(pluginId)
        return reloaded != null
    }
    
    fun listPlugins(): List<PluginInfo> {
        return pluginManager.getAll().map { 
            PluginInfo(
                id = it.id.value,
                version = it.version.toString(),
                type = it.type.name.lowercase(),
                source = it.source.name.lowercase(),
                status = it.status.name.lowercase(),
                enabled = it.isEnabled
            )
        }
    }

    private fun startPlugin(id: PluginId) {
        val plugin = pluginManager.get(id) ?: return
        
        val handler = CoroutineExceptionHandler { _, throwable ->
            println("[${id.value}] coroutine error: ${throwable.message}")
        }
        
        val scope = CoroutineScope(Dispatchers.IO + handler)
        runningPlugins[id] = scope
        
        scope.launch {
            println("[${id.value}] starting")
            plugin.instance.start { inbound ->
                handleInboundMessage(plugin.instance, inbound)
            }
        }
    }
    
    private suspend fun handleInboundMessage(plugin: ChannelPort, inbound: InboundMessage) {
        val sessionKey = buildSessionKey(inbound)
        indexStore.touchSession(sessionKey)
        
        println(
            "[${plugin.id}] message chat=${inbound.chatId} user=${inbound.userId} " +
                "group=${inbound.isGroup} mentioned=${inbound.isMentioned}"
        )
        
        // Echo for now - replace with actual agent routing
        plugin.send(
            OutboundMessage(
                channelId = inbound.channelId,
                chatId = inbound.chatId,
                text = inbound.text
            )
        ).onFailure { error ->
            println("[${plugin.id}] send failed: ${error.message}")
        }
    }
    
    private fun buildSessionKey(inbound: InboundMessage): String {
        return if (inbound.isGroup) {
            "agent:${config.agent.id}:${inbound.channelId}:group:${inbound.chatId}"
        } else {
            "agent:${config.agent.id}:main"
        }
    }
    
    /**
     * Simple event listener that logs plugin events
     */
    private inner class LoggingEventListener : PluginEventListener {
        override fun onEvent(event: PluginEvent) {
            when (event) {
                is PluginDiscovered -> 
                    println("[plugin] discovered: ${event.pluginId} v${event.version} (${event.source.name.lowercase()})")
                is PluginLoaded -> 
                    println("[plugin] loaded: ${event.pluginId} v${event.version} (${event.source.name.lowercase()})")
                is PluginEnabled -> 
                    println("[plugin] enabled: ${event.pluginId}")
                is PluginDisabled -> 
                    println("[plugin] disabled: ${event.pluginId}")
                is PluginReloaded -> 
                    println("[plugin] reloaded: ${event.pluginId}")
                is PluginError -> 
                    println("[plugin] error: ${event.pluginId} - ${event.message}")
                is PluginSkipped -> 
                    println("[plugin] skipped: ${event.pluginId} - ${event.reason}")
            }
        }
    }
}
