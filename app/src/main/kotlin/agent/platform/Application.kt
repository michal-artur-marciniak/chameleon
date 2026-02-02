package agent.platform

import agent.platform.config.ConfigLoader
import agent.platform.config.StartupLogger
import agent.platform.plugins.PluginService
import agent.platform.server.ServerFactory

fun main() {
    val loaded = ConfigLoader().load()
    
    StartupLogger().log(loaded.config, loaded.configPath, loaded.envPath)

    val server = ServerFactory().create(
        host = loaded.config.gateway.host,
        port = loaded.config.gateway.port
    )

    PluginService(loaded.config, loaded.configPath).startAll()

    server.start(wait = true)
}
