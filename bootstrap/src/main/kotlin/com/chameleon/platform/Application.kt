package com.chameleon

import com.chameleon.config.ConfigLoader
import com.chameleon.config.StartupLogger
import com.chameleon.logging.LoggingConfigurator
import com.chameleon.plugins.PluginService
import com.chameleon.server.ServerFactory

fun main(args: Array<String>) {
    val loaded = ConfigLoader().load(args.toList())
    LoggingConfigurator.apply(loaded.config.logging)
    
    StartupLogger().log(loaded.config, loaded.configPath, loaded.envPath)

    val server = ServerFactory().create(
        host = loaded.config.gateway.host,
        port = loaded.config.gateway.port
    )

    PluginService(loaded.config).startAll()

    server.start(wait = true)
}
