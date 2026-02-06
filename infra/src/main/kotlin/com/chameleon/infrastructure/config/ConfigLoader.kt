package com.chameleon.infrastructure.config

import kotlinx.serialization.json.Json
import com.chameleon.config.domain.LogFormat
import com.chameleon.config.domain.LogLevel
import com.chameleon.config.domain.PlatformConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Loads platform configuration from JSON files with environment variable expansion.
 *
 * Supports:
 * - Auto-discovery of config/config.json (searches upward from working directory)
 * - Explicit config path via --config= CLI argument
 * - .env file loading for environment variables
 * - ${ENV_VAR} syntax for environment variable substitution in config
 * - Logging overrides from CLI args, env vars, and config
 */
class ConfigLoader(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Loads configuration with auto-discovery.
     */
    fun load(): LoadedConfig {
        return load(ConfigPath.Auto, emptyMap(), emptyList())
    }

    /**
     * Loads configuration with CLI argument parsing for config path.
     */
    fun load(cliArgs: List<String>): LoadedConfig {
        val resolved = resolveConfigPathFromCli(cliArgs) ?: ConfigPath.Auto
        return load(resolved, emptyMap(), cliArgs)
    }

    /**
     * Loads configuration with full control over sources.
     *
     * Priority (highest to lowest): CLI args → env vars → config file → defaults
     *
     * @param configPath Path resolution strategy (auto or explicit)
     * @param envOverrides Additional environment variable overrides
     * @param cliArgs CLI arguments for logging overrides and config path
     */
    fun load(
        configPath: ConfigPath = ConfigPath.Auto,
        envOverrides: Map<String, String> = emptyMap(),
        cliArgs: List<String> = emptyList()
    ): LoadedConfig {
        val resolvedPath = when (configPath) {
            ConfigPath.Auto -> resolveConfigPath()
            is ConfigPath.Explicit -> ResolvedConfigPath.Found(configPath.path)
        }
        val envPath = resolveEnvPath(resolvedPath)
        val env = loadDotEnv(envPath) + envOverrides
        val config = loadConfig(resolvedPath, env)
        val overrides = resolveLoggingOverrides(config, env, cliArgs)
        val effective = applyLoggingOverrides(config, overrides)
        val pathOrNull = (resolvedPath as? ResolvedConfigPath.Found)?.path
        return LoadedConfig(effective, pathOrNull, envPath)
    }

    private fun loadConfig(path: ResolvedConfigPath, env: Map<String, String>): PlatformConfig {
        val content = when (path) {
            is ResolvedConfigPath.Found -> if (Files.exists(path.path)) {
                Files.readString(path.path)
            } else {
                null
            }
            ResolvedConfigPath.Missing -> null
        } ?: javaClass.classLoader.getResource("config.default.json")?.readText()
        ?: return PlatformConfig()
        val expanded = expandEnv(content, env)
        return json.decodeFromString(PlatformConfig.serializer(), expanded)
    }

    private fun expandEnv(raw: String, env: Map<String, String>): String {
        val regex = Regex("""\$\{([A-Z0-9_]+)}""")
        return regex.replace(raw) { match ->
            val key = match.groupValues[1]
            env[key] ?: System.getenv(key).orEmpty()
        }
    }

    private fun loadDotEnv(path: Path): Map<String, String> {
        if (!Files.exists(path)) return emptyMap()
        return Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val cleaned = if (line.startsWith("export ")) line.removePrefix("export ").trim() else line
                val index = cleaned.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = cleaned.substring(0, index).trim()
                val rawValue = cleaned.substring(index + 1).trim()
                val value = rawValue.trim().trim('"').trim('\'')
                key to value
            }
            .toMap()
    }

    private fun resolveConfigPath(): ResolvedConfigPath {
        var dir = Paths.get("").toAbsolutePath()
        while (true) {
            val candidate = dir.resolve("config/config.json")
            if (Files.exists(candidate)) return ResolvedConfigPath.Found(candidate)
            val parent = dir.parent ?: return ResolvedConfigPath.Missing
            if (parent == dir) return ResolvedConfigPath.Missing
            dir = parent
        }
    }

    private fun resolveConfigPathFromCli(args: List<String>): ConfigPath? {
        val match = args.firstOrNull { it.startsWith("--config=") } ?: return null
        val value = match.substringAfter("=").trim()
        if (value.isEmpty()) return null
        return ConfigPath.Explicit(Paths.get(value))
    }

    private fun resolveEnvPath(configPath: ResolvedConfigPath): Path {
        val root = (configPath as? ResolvedConfigPath.Found)?.path?.parent?.parent
        if (root != null) {
            val candidate = root.resolve(".env")
            if (Files.exists(candidate)) return candidate
        }
        return Paths.get(".env")
    }

    private fun resolveLoggingOverrides(
        config: PlatformConfig,
        env: Map<String, String>,
        cliArgs: List<String>
    ): LoggingOverrides {
        val configOverrides = LogOverrides(
            level = config.logging.level.name.lowercase(),
            format = config.logging.format.name.lowercase(),
            debug = config.logging.debug,
            stacktrace = config.logging.stacktrace
        )
        val envOverrides = LogOverrides(
            level = env["LOG_LEVEL"],
            format = env["LOG_FORMAT"],
            debug = env["LOG_DEBUG"]?.toBooleanStrictOrNull(),
            stacktrace = env["LOG_STACKTRACE"]?.toBooleanStrictOrNull()
        )
        val cli = parseCliArgs(cliArgs)
        return LoggingOverrides(configOverrides, envOverrides, cli)
    }

    private fun parseCliArgs(args: List<String>): LogOverrides {
        val values = args.mapNotNull { arg ->
            when {
                arg.startsWith("--log-level=") -> "level" to arg.substringAfter("=")
                arg.startsWith("--log-format=") -> "format" to arg.substringAfter("=")
                arg.startsWith("--log-debug=") -> "debug" to arg.substringAfter("=")
                arg.startsWith("--log-stacktrace=") -> "stacktrace" to arg.substringAfter("=")
                else -> null
            }
        }.toMap()

        return LogOverrides(
            level = values["level"],
            format = values["format"],
            debug = values["debug"]?.toBooleanStrictOrNull(),
            stacktrace = values["stacktrace"]?.toBooleanStrictOrNull()
        )
    }

    private fun applyLoggingOverrides(
        config: PlatformConfig,
        overrides: LoggingOverrides
    ): PlatformConfig {
        val resolved = overrides.resolve()
        val level = resolved.level?.let { LogLevel.valueOf(it.uppercase()) } ?: config.logging.level
        val format = resolved.format?.let { LogFormat.valueOf(it.uppercase()) } ?: config.logging.format
        val debug = resolved.debug
        val stacktrace = if (debug) true else resolved.stacktrace
        return config.copy(
            logging = config.logging.copy(
                level = if (debug) LogLevel.DEBUG else level,
                format = format,
                debug = debug,
                stacktrace = stacktrace
            )
        )
    }
}

/**
 * Result of loading configuration.
 *
 * @property config The effective platform configuration
 * @property configPath Path to the loaded config file (null if using defaults)
 * @property envPath Path to the loaded .env file
 */
data class LoadedConfig(
    val config: PlatformConfig,
    val configPath: Path?,
    val envPath: Path
)
