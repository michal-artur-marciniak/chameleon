package agent.platform.config

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ConfigLoader(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(): LoadedConfig {
        val configPath = resolveConfigPath()
        val envPath = resolveEnvPath(configPath)
        val env = loadDotEnv(envPath)
        val config = loadConfig(configPath, env)
        return LoadedConfig(config, configPath, envPath)
    }

    private fun loadConfig(path: Path?, env: Map<String, String>): PlatformConfig {
        val content = if (path != null && Files.exists(path)) {
            Files.readString(path)
        } else {
            // Load default config from resources
            javaClass.classLoader.getResource("config.default.json")?.readText()
                ?: return PlatformConfig()
        }
        return json.decodeFromString(PlatformConfig.serializer(), expandEnv(content, env))
    }

    private fun expandEnv(raw: String, env: Map<String, String>): String {
        val regex = Regex("""\$\{([A-Z0-9_]+)}""")
        return regex.replace(raw) { match ->
            val key = match.groupValues[1]
            env[key] ?: System.getenv(key) ?: match.value
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

    private fun resolveConfigPath(): Path? {
        var dir = Paths.get("").toAbsolutePath()
        while (true) {
            val candidate = dir.resolve("config/config.json")
            if (Files.exists(candidate)) return candidate
            val parent = dir.parent ?: return null
            if (parent == dir) return null
            dir = parent
        }
    }

    private fun resolveEnvPath(configPath: Path?): Path {
        if (configPath != null) {
            val root = configPath.parent?.parent
            if (root != null) {
                val candidate = root.resolve(".env")
                if (Files.exists(candidate)) return candidate
            }
        }
        return Paths.get(".env")
    }
}

data class LoadedConfig(
    val config: PlatformConfig,
    val configPath: Path?,
    val envPath: Path
)
