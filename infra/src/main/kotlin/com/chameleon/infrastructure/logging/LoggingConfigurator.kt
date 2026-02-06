package com.chameleon.infrastructure.logging

import com.chameleon.config.domain.LoggingConfig
import org.slf4j.LoggerFactory

/**
 * Applies logging configuration to system properties.
 *
 * Sets LOG_LEVEL, LOG_FORMAT, and LOG_STACKTRACE properties
 * that are consumed by the logging framework.
 */
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
