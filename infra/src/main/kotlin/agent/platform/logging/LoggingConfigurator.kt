package agent.platform.logging

import agent.platform.config.LoggingConfig
import org.slf4j.LoggerFactory

object LoggingConfigurator {
    fun apply(config: LoggingConfig) {
        setProperty("LOG_LEVEL", config.level.name)
        setProperty("LOG_FORMAT", config.format.name)
        setProperty("LOG_STACKTRACE", config.stacktrace.toString())
        if (config.debug) {
            setProperty("LOG_LEVEL", "DEBUG")
            setProperty("LOG_STACKTRACE", "true")
        }
        LoggerFactory.getLogger(LoggingConfigurator::class.java).info(
            "logging configured level={} format={} debug={} stacktrace={}",
            getProperty("LOG_LEVEL"),
            getProperty("LOG_FORMAT"),
            config.debug,
            getProperty("LOG_STACKTRACE")
        )
    }

    private fun setProperty(key: String, value: String) {
        System.setProperty(key, value)
    }

    private fun getProperty(key: String): String? {
        return System.getProperty(key)
    }
}
