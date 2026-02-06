package com.chameleon.bootstrap

import com.chameleon.bootstrap.StartupLogger
import com.chameleon.config.ConfigLoader
import com.chameleon.logging.LoggingConfigurator

/**
 * Main entry point for the Chameleon platform.
 *
 * Orchestrates the bootstrap sequence:
 * 1. Load configuration from files and environment
 * 2. Configure logging based on platform settings
 * 3. Log startup configuration for diagnostics
 * 4. Create and start the HTTP gateway server
 * 5. Load and start all enabled channel plugins
 * 6. Block until server termination
 *
 * @param args Command line arguments passed to ConfigLoader
 */
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
