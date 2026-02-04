package agent.platform.config

import java.nio.file.Path
import org.slf4j.LoggerFactory

class StartupLogger {
    private val logger = LoggerFactory.getLogger(StartupLogger::class.java)

    fun log(config: PlatformConfig, configPath: Path?, envPath: Path) {
        val telegramEnabled = config.channels.telegram.enabled
        val tokenPresent = !config.channels.telegram.token.isNullOrBlank()
        val defaultAgentId = config.agents.list.firstOrNull { it.default }?.id ?: "main"
        val configLog = configPath?.toAbsolutePath()?.toString() ?: "<not found>"
        logger.info("[app] config={}", configLog)
        logger.info("[app] env={}", envPath.toAbsolutePath())
        logger.info("[app] gateway={}:{}", config.gateway.host, config.gateway.port)
        logger.info("[app] agent default={}", defaultAgentId)
        logger.info("[app] telegram enabled={} token_present={}", telegramEnabled, tokenPresent)
        if (configPath == null) {
            logger.warn("[app] warning: config/config.json not found; using defaults")
        }
    }
}
