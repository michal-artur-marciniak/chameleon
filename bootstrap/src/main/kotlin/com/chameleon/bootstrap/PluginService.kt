package com.chameleon.bootstrap

import com.chameleon.agent.application.HandleInboundMessageUseCase
import com.chameleon.config.domain.PlatformConfig
import com.chameleon.infrastructure.agent.AgentRuntimeFactory
import com.chameleon.infrastructure.logging.LogWrapper
import com.chameleon.plugin.domain.PluginEvent
import com.chameleon.plugin.domain.PluginEventListener
import com.chameleon.plugin.domain.PluginId
import com.chameleon.plugin.domain.PluginInfo
import com.chameleon.plugin.domain.PluginManager
import com.chameleon.plugin.domain.PluginDiscovered
import com.chameleon.plugin.domain.PluginLoaded
import com.chameleon.plugin.domain.PluginEnabled
import com.chameleon.plugin.domain.PluginDisabled
import com.chameleon.plugin.domain.PluginReloaded
import com.chameleon.plugin.domain.PluginError
import com.chameleon.plugin.domain.PluginSkipped
import com.chameleon.sdk.ChannelPort
import com.chameleon.sdk.InboundMessage
import com.chameleon.infrastructure.plugins.FileSystemPluginRepository
import com.chameleon.infrastructure.plugins.OfficialPluginRegistry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Application service that orchestrates the Plugin domain.
 *
 * This is NOT the domain - it uses the [PluginManager] aggregate root
 * to handle plugin lifecycle and coordinates with other contexts.
 *
 * Responsibilities:
 * - Load all plugins through the domain layer
 * - Start enabled plugins in separate coroutine scopes
 * - Route inbound messages to the agent runtime via UC-001
 * - Manage plugin lifecycle (enable, disable, reload)
 * - Log plugin events for observability
 *
 * @property config Platform configuration for plugins and agents
 * @property logger SLF4J logger instance for this class
 * @property stacktrace Whether to include stacktraces in error logs
 * @property extensionsDir Directory containing external plugin definitions
 * @property pluginManager Domain aggregate root for plugin lifecycle
 * @property runningPlugins Map of running plugin coroutine scopes
 * @property agentRuntime Runtime for executing agent conversations
 * @property handleInboundMessageUseCase Use case for processing inbound messages
 */
class PluginService(
    private val config: PlatformConfig
) {
    private val logger = LoggerFactory.getLogger(PluginService::class.java)
    private val stacktrace = config.logging.stacktrace
    private val extensionsDir: Path = Paths.get(config.agents.defaults.extensionsDir)
    
    private val pluginManager: PluginManager
    private val runningPlugins = mutableMapOf<PluginId, CoroutineScope>()
    private val agentRuntime = AgentRuntimeFactory.create(config)
    private val handleInboundMessageUseCase = HandleInboundMessageUseCase(agentRuntime)
    
    init {
        val officialRegistry = OfficialPluginRegistry()
        val externalRepository = FileSystemPluginRepository(extensionsDir)
        pluginManager = PluginManager(config, officialRegistry, externalRepository)
        pluginManager.addEventListener(LoggingEventListener())
    }

    /**
     * Loads and starts all enabled plugins.
     *
     * Loads plugins through the domain layer, then starts each enabled
     * plugin in its own coroutine scope with exception handling.
     */
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
    
    /**
     * Stops all running plugins.
     *
     * Cancels coroutine scopes for all running plugins.
     * Note: Clean shutdown should call ChannelPort.stop() on each plugin.
     */
    fun stopAll() {
        runningPlugins.forEach { (id, scope) ->
            logger.info("[{}] stopping", id.value)
            // Cancel the scope
            // Note: ChannelPort.stop() should be called for clean shutdown
        }
        runningPlugins.clear()
    }
    
    /**
     * Enables a plugin by its identifier.
     *
     * @param id The plugin identifier
     * @return true if the plugin was enabled, false otherwise
     */
    fun enablePlugin(id: String): Boolean {
        return pluginManager.enable(PluginId(id))
    }
    
    /**
     * Disables a plugin by its identifier.
     *
     * @param id The plugin identifier
     * @return true if the plugin was disabled, false otherwise
     */
    fun disablePlugin(id: String): Boolean {
        return pluginManager.disable(PluginId(id))
    }
    
    /**
     * Reloads a plugin by its identifier.
     *
     * @param id The plugin identifier
     * @return true if the plugin was reloaded, false otherwise
     */
    fun reloadPlugin(id: String): Boolean {
        val pluginId = PluginId(id)
        val reloaded = pluginManager.reload(pluginId)
        return reloaded != null
    }
    
    /**
     * Lists all plugins with their current status.
     *
     * @return List of [PluginInfo] containing plugin metadata and status
     */
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

    /**
     * Starts a single plugin in a new coroutine scope.
     *
     * Creates a coroutine scope with exception handling and launches
     * the plugin's start method. Inbound messages are routed to
     * [handleInboundMessage] for processing.
     *
     * @param id The plugin identifier to start
     */
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
    
    /**
     * Handles an inbound message from a plugin.
     *
     * Logs message details and delegates to UC-001 [HandleInboundMessageUseCase]
     * to process the message through the DDD agent runtime.
     *
     * @param plugin The channel port that received the message
     * @param inbound The inbound message to process
     */
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
     * Event listener that logs plugin lifecycle events.
     *
     * Implements [PluginEventListener] to receive and log all plugin
     * domain events for observability and debugging.
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
