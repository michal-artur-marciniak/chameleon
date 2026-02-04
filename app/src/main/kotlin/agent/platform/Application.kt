package agent.platform

import agent.platform.agent.LoggingDomainEventPublisher
import agent.platform.config.ConfigLoader
import agent.platform.config.StartupLogger
import agent.platform.logging.LoggingConfigurator
import agent.platform.plugins.PluginService
import agent.platform.server.ServerFactory

fun main(args: Array<String>) {
    val loaded = ConfigLoader().load(args.toList())
    LoggingConfigurator.apply(loaded.config.logging)
    
    StartupLogger().log(loaded.config, loaded.configPath, loaded.envPath)

    val server = ServerFactory().create(
        host = loaded.config.gateway.host,
        port = loaded.config.gateway.port
    )

    val eventPublisher = LoggingDomainEventPublisher()
    PluginService(loaded.config, loaded.configPath, eventPublisher).startAll()

    server.start(wait = true)
}
