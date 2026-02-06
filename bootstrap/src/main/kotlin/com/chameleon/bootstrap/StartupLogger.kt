package com.chameleon.bootstrap

import com.chameleon.config.domain.PlatformConfig
import com.chameleon.plugin.telegram.TelegramConfig
import kotlinx.serialization.json.Json
import java.nio.file.Path
import org.slf4j.LoggerFactory

/**
 * Logs startup configuration and diagnostic information.
 *
 * Outputs key configuration values at application startup to aid in
 * debugging and operational visibility. Logs include:
 * - Configuration file path
 * - Environment file path
 * - Gateway binding details
 * - Default agent identifier
 * - Telegram channel status
 *
 * @property logger SLF4J logger instance for this class
 */
class StartupLogger {
    private val logger = LoggerFactory.getLogger(StartupLogger::class.java)

    /**
     * Logs the platform configuration at startup.
     *
     * @param config The loaded platform configuration
     * @param configPath Path to the configuration file, or null if using defaults
     * @param envPath Path to the environment file
     */
    fun log(config: PlatformConfig, configPath: Path?, envPath: Path) {
        val telegramConfig = resolveTelegramConfig(config)
        val telegramEnabled = telegramConfig.enabled
        val tokenPresent = !telegramConfig.token.isNullOrBlank()
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

    private fun resolveTelegramConfig(config: PlatformConfig): TelegramConfig {
        val json = Json { ignoreUnknownKeys = true }
        val raw = config.channels.get("telegram") ?: return TelegramConfig()
        return json.decodeFromJsonElement(TelegramConfig.serializer(), raw)
    }
}
