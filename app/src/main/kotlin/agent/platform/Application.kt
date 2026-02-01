package agent.platform

import agent.platform.config.ConfigLoader
import agent.platform.config.StartupLogger
import agent.platform.plugins.InitializationResult
import agent.platform.plugins.PluginService
import agent.platform.server.ServerFactory
import java.nio.file.Paths

fun main() {
    val loaded = ConfigLoader().load()
    
    StartupLogger().log(loaded.config, loaded.configPath, loaded.envPath)

    val server = ServerFactory().create(
        host = loaded.config.gateway.host,
        port = loaded.config.gateway.port
    )

    val pluginsDir = loaded.configPath?.parent?.resolve("plugins") ?: Paths.get("plugins")
    when (PluginService(loaded.config, pluginsDir).initialize()) {
        is InitializationResult.NoPluginsLoaded -> println("[app] no plugins loaded")
        is InitializationResult.Success -> { /* plugins started */ }
    }

    server.start(wait = true)
}
