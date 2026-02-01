package agent.platform

import agent.platform.config.ConfigLoader
import agent.platform.config.StartupLogger
import agent.platform.plugins.InitializationResult
import agent.platform.plugins.PluginManager
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

    // Initialize plugins
    val orchestrator = PluginOrchestrator(loadedConfig.config)
    val pluginsDir = loadedConfig.configPath?.parent?.resolve("plugins") ?: Paths.get("plugins")
    val pluginManager = PluginManager(
        config = loadedConfig.config,
        orchestrator = orchestrator,
        pluginsDir = pluginsDir
    )
    
    when (val result = pluginManager.initialize()) {
        is InitializationResult.NoPluginsLoaded -> {
            println("[app] no plugins loaded")
        }
        is InitializationResult.Success -> {
            println("[app] started ${result.count} plugin(s)")
        }
    }

    // Start server (blocks until shutdown)
    server.start(wait = true)
}
