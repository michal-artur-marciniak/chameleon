package agent.platform.plugins

import agent.sdk.ChannelPort
import agent.sdk.PluginManifest
import agent.plugin.telegram.TelegramPlugin
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

data class LoadedPlugin(
    val manifest: PluginManifest,
    val plugin: ChannelPort
)

class PluginLoader(
    private val pluginsDir: Path,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun loadEnabledPlugins(enabledChannels: Map<String, ChannelConfig>): List<LoadedPlugin> {
        val manifests = loadBuiltins()
        return manifests.mapNotNull { manifest ->
            instantiatePlugin(manifest, enabledChannels[manifest.id])
        }
    }

    private fun loadBuiltins(): List<PluginManifest> {
        val telegramManifest = pluginsDir.resolve("telegram/plugin.json")
        return listOfNotNull(loadManifest(telegramManifest))
    }

    private fun loadManifest(path: Path): PluginManifest? {
        if (!Files.exists(path)) return null
        val content = Files.readString(path)
        return json.decodeFromString(PluginManifest.serializer(), content)
    }

    private fun instantiatePlugin(manifest: PluginManifest, config: ChannelConfig?): LoadedPlugin? {
        if (config == null || !config.enabled) return null

        return when (manifest.id) {
            "telegram" -> {
                val token = config.token
                if (token.isNullOrBlank()) {
                    println("[loader] ${manifest.id}: enabled but token missing")
                    return null
                }
                val plugin = TelegramPlugin(token = token, requireMentionInGroups = true)
                LoadedPlugin(manifest, plugin)
            }
            else -> {
                println("[loader] ${manifest.id}: unknown plugin type")
                null
            }
        }
    }
}

data class ChannelConfig(
    val enabled: Boolean,
    val token: String? = null
)
