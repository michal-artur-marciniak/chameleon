package agent.platform.plugins

import agent.platform.application.HandleInboundMessageUseCase
import agent.platform.config.PlatformConfig
import agent.platform.logging.LogWrapper
import agent.platform.agent.AgentRuntimeFactory
import agent.platform.agent.domain.DomainEventPublisherPort
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
import agent.platform.plugins.FileSystemPluginRepository
import agent.platform.plugins.OfficialPluginRegistry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
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
    private val configPath: Path?,
    private val eventPublisher: DomainEventPublisherPort? = null
) {
    private val logger = LoggerFactory.getLogger(PluginService::class.java)
    private val stacktrace = config.logging.stacktrace
    private val extensionsDir: Path = Paths.get(config.agents.defaults.extensionsDir)
    
    private val pluginManager: PluginManager
    private val runningPlugins = mutableMapOf<PluginId, CoroutineScope>()
    private val agentRuntime = AgentRuntimeFactory.create(config)
    private val handleInboundMessageUseCase = HandleInboundMessageUseCase(agentRuntime, eventPublisher)
    
    init {
        val officialRegistry = OfficialPluginRegistry()
        val externalRepository = FileSystemPluginRepository(extensionsDir)
        pluginManager = PluginManager(config, officialRegistry, externalRepository)
        pluginManager.addEventListener(LoggingEventListener())
    }

    fun startAll() {
        // Load all plugins through the domain
        logger.info("plugin external dir={}", extensionsDir)
        val plugins = pluginManager.loadAll()

        if (plugins.isEmpty()) {
            logger.warn("[app] no plugins loaded")
            return
        }

        // Start enabled plugins
        plugins.filter { it.isEnabled }.forEach { plugin ->
            startPlugin(plugin.id)
        }
    }
    
    fun stopAll() {
        runningPlugins.forEach { (id, scope) ->
            logger.info("[{}] stopping", id.value)
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
            LogWrapper.error(logger, "[${id.value}] coroutine error", throwable, stacktrace)
        }
        
        val scope = CoroutineScope(Dispatchers.IO + handler)
        runningPlugins[id] = scope
        
        scope.launch {
            logger.info("[{}] starting", id.value)
            plugin.instance.start { inbound ->
                handleInboundMessage(plugin.instance, inbound)
            }
        }
    }
    
    private suspend fun handleInboundMessage(plugin: ChannelPort, inbound: InboundMessage) {
        logger.info(
            "[{}] message chat={} user={} group={} mentioned={}",
            plugin.id,
            inbound.chatId,
            inbound.userId,
            inbound.isGroup,
            inbound.isMentioned
        )

        val agentId = config.agents.list.firstOrNull { it.default }?.id ?: "main"
        
        // Use UC-001: HandleInboundMessageUseCase to process the message through DDD agent runtime
        handleInboundMessageUseCase.executeAndRespond(
            channel = plugin,
            inbound = inbound,
            agentId = agentId
        )
    }
    
    /**
     * Simple event listener that logs plugin events
     */
    private inner class LoggingEventListener : PluginEventListener {
        override fun onEvent(event: PluginEvent) {
            when (event) {
                is PluginDiscovered -> 
                    logger.info(
                        "[plugin] discovered: {} v{} ({})",
                        event.pluginId,
                        event.version,
                        event.source.name.lowercase()
                    )
                is PluginLoaded -> 
                    logger.info(
                        "[plugin] loaded: {} v{} ({})",
                        event.pluginId,
                        event.version,
                        event.source.name.lowercase()
                    )
                is PluginEnabled -> 
                    logger.info("[plugin] enabled: {}", event.pluginId)
                is PluginDisabled -> 
                    logger.info("[plugin] disabled: {}", event.pluginId)
                is PluginReloaded -> 
                    logger.info("[plugin] reloaded: {}", event.pluginId)
                is PluginError -> 
                    logger.error("[plugin] error: {} - {}", event.pluginId, event.message)
                is PluginSkipped -> 
                    logger.info("[plugin] skipped: {} - {}", event.pluginId, event.reason)
            }
        }
    }
}
