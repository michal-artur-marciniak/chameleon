package agent.platform

import agent.platform.config.ConfigLoader
import agent.platform.config.StartupLogger
import agent.platform.plugins.ChannelConfig
import agent.platform.plugins.PluginLoader
import agent.platform.plugins.PluginOrchestrator
import agent.platform.server.ServerFactory
import java.nio.file.Paths

fun main() {
    // Load configuration
    val configLoader = ConfigLoader()
    val loadedConfig = configLoader.load()
    
    // Log startup info
    StartupLogger().log(loadedConfig.config, loadedConfig.configPath, loadedConfig.envPath)

    // Create and configure server
    val serverFactory = ServerFactory()
    val server = serverFactory.create(
        host = loadedConfig.config.gateway.host,
        port = loadedConfig.config.gateway.port
    )

    // Load and start plugins
    val pluginsDir = loadedConfig.configPath?.parent?.resolve("plugins") 
        ?: Paths.get("plugins")
    val pluginLoader = PluginLoader(pluginsDir)
    
    val channelConfigs = mapOf(
        "telegram" to ChannelConfig(
            enabled = loadedConfig.config.channels.telegram.enabled,
            token = loadedConfig.config.channels.telegram.token
        )
    )
    
    val loadedPlugins = pluginLoader.loadEnabledPlugins(channelConfigs)
    
    if (loadedPlugins.isEmpty()) {
        println("[app] no plugins loaded")
    } else {
        val orchestrator = PluginOrchestrator(loadedConfig.config)
        loadedPlugins.forEach { loaded ->
            orchestrator.startPlugin(loaded.plugin)
        }
    }

    // Start server (blocks until shutdown)
    server.start(wait = true)
}
