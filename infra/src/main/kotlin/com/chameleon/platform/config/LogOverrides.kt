package com.chameleon.config

data class LogOverrides(
    val level: String? = null,
    val format: String? = null,
    val debug: Boolean? = null,
    val stacktrace: Boolean? = null
)

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

data class ResolvedLogging(
    val level: String?,
    val format: String?,
    val debug: Boolean,
    val stacktrace: Boolean
)
