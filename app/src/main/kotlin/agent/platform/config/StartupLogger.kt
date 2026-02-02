package agent.platform.config

import java.nio.file.Path

class StartupLogger {
    fun log(config: PlatformConfig, configPath: Path?, envPath: Path) {
        val telegramEnabled = config.channels.telegram.enabled
        val tokenPresent = !config.channels.telegram.token.isNullOrBlank()
        val configLog = configPath?.toAbsolutePath()?.toString() ?: "<not found>"
        println("[app] config=$configLog")
        println("[app] env=${envPath.toAbsolutePath()}")
        println("[app] gateway=${config.gateway.host}:${config.gateway.port}")
        println("[app] telegram enabled=$telegramEnabled token_present=$tokenPresent")
        if (configPath == null) {
            println("[app] warning: config/config.json not found; using defaults")
        }
    }
}
