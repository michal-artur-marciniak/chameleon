package agent.platform.plugins

import agent.platform.config.PlatformConfig
import agent.sdk.ChannelPort
import agent.sdk.PluginManifest
import agent.plugin.telegram.TelegramPlugin
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PluginManager(
    private val config: PlatformConfig,
    private val orchestrator: PluginOrchestrator,
    private val pluginsDir: Path = Paths.get("plugins"),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun initialize(): InitializationResult {
        val loadedPlugins = loadPlugins()
        
        if (loadedPlugins.isEmpty()) {
            return InitializationResult.NoPluginsLoaded
        }
        
        loadedPlugins.forEach { plugin ->
            orchestrator.startPlugin(plugin)
        }
        
        return InitializationResult.Success(loadedPlugins.size)
    }
    
    private fun loadPlugins(): List<ChannelPort> {
        val manifests = loadManifests()
        return manifests.mapNotNull { manifest ->
            instantiatePlugin(manifest)
        }
    }
    
    private fun loadManifests(): List<PluginManifest> {
        val telegramManifest = pluginsDir.resolve("telegram/plugin.json")
        return listOfNotNull(loadManifest(telegramManifest))
    }
    
    private fun loadManifest(path: Path): PluginManifest? {
        if (!Files.exists(path)) return null
        return runCatching {
            json.decodeFromString(PluginManifest.serializer(), Files.readString(path))
        }.getOrNull()
    }
    
    private fun instantiatePlugin(manifest: PluginManifest): ChannelPort? {
        return when (manifest.id) {
            "telegram" -> instantiateTelegram()
            else -> {
                println("[plugin-manager] ${manifest.id}: unknown plugin type")
                null
            }
        }
    }
    
    private fun instantiateTelegram(): ChannelPort? {
        val telegramConfig = config.channels.telegram
        
        if (!telegramConfig.enabled) {
            return null
        }
        
        val token = telegramConfig.token
        if (token.isNullOrBlank()) {
            println("[plugin-manager] telegram: enabled but token missing")
            return null
        }
        
        return TelegramPlugin(token = token, requireMentionInGroups = true)
    }
}

sealed class InitializationResult {
    data object NoPluginsLoaded : InitializationResult()
    data class Success(val count: Int) : InitializationResult()
}
