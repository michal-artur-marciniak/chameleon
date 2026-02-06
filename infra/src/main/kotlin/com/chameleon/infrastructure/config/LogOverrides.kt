package com.chameleon.config

/**
 * Individual logging configuration overrides.
 *
 * All fields are nullable to represent "not specified" state,
 * allowing merge semantics with other sources.
 */
data class LogOverrides(
    val level: String? = null,
    val format: String? = null,
    val debug: Boolean? = null,
    val stacktrace: Boolean? = null
)

/**
 * Aggregates logging overrides from multiple sources.
 *
 * Merges config file, environment variables, and CLI arguments
 * with priority: CLI > env > config.
 */
data class LoggingOverrides(
    val config: LogOverrides = LogOverrides(),
    val env: LogOverrides = LogOverrides(),
    val cli: LogOverrides = LogOverrides()
) {
    fun resolve(): ResolvedLogging {
        val level = cli.level ?: env.level ?: config.level
        val format = cli.format ?: env.format ?: config.format
        val debug = cli.debug ?: env.debug ?: config.debug ?: false
        val stacktrace = cli.stacktrace ?: env.stacktrace ?: config.stacktrace ?: false
        return ResolvedLogging(level, format, debug, stacktrace)
    }
}

/**
 * Final resolved logging configuration after merging all sources.
 */
data class ResolvedLogging(
    val level: String?,
    val format: String?,
    val debug: Boolean,
    val stacktrace: Boolean
)
