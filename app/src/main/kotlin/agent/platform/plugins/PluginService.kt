package agent.platform.plugins

import agent.platform.config.PlatformConfig
import agent.platform.persistence.SessionIndexStore
import agent.sdk.ChannelPort
import agent.sdk.OutboundMessage
import agent.sdk.PluginManifest
import agent.plugin.telegram.TelegramPlugin
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PluginService(
    private val config: PlatformConfig,
    private val pluginsDir: Path = Paths.get("plugins"),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val workspace = Paths.get(config.agent.workspace)
    private val indexStore = SessionIndexStore(workspace)

    fun initialize(): InitializationResult {
        val plugins = loadPlugins()
        
        if (plugins.isEmpty()) {
            return InitializationResult.NoPluginsLoaded
        }
        
        plugins.forEach { plugin ->
            startPlugin(plugin)
        }
        
        return InitializationResult.Success(plugins.size)
    }
    
    private fun loadPlugins(): List<ChannelPort> {
        val telegramManifest = pluginsDir.resolve("telegram/plugin.json")
        return listOfNotNull(
            loadManifest(telegramManifest)?.let { instantiatePlugin(it) }
        )
    }
    
    private fun loadManifest(path: Path): PluginManifest? {
        if (!Files.exists(path)) return null
        return runCatching {
            json.decodeFromString(PluginManifest.serializer(), Files.readString(path))
        }.getOrNull()
    }
    
    private fun instantiatePlugin(manifest: PluginManifest): ChannelPort? {
        return when (manifest.id) {
            "telegram" -> {
                val telegramConfig = config.channels.telegram
                if (!telegramConfig.enabled) return null
                
                val token = telegramConfig.token
                if (token.isNullOrBlank()) {
                    println("[plugin-service] telegram: enabled but token missing")
                    return null
                }
                
                TelegramPlugin(token = token, requireMentionInGroups = true)
            }
            else -> {
                println("[plugin-service] ${manifest.id}: unknown plugin type")
                null
            }
        }
    }
    
    private fun startPlugin(plugin: ChannelPort) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            println("[${plugin.id}] coroutine error: ${throwable.message}")
        }
        CoroutineScope(Dispatchers.IO + handler).launch {
            println("[${plugin.id}] starting")
            plugin.start { inbound ->
                val sessionKey = buildSessionKey(inbound)
                indexStore.touchSession(sessionKey)
                
                println(
                    "[${plugin.id}] message chat=${inbound.chatId} user=${inbound.userId} " +
                        "group=${inbound.isGroup} mentioned=${inbound.isMentioned}"
                )
                
                plugin.send(
                    OutboundMessage(
                        channelId = inbound.channelId,
                        chatId = inbound.chatId,
                        text = inbound.text
                    )
                ).onFailure { error ->
                    println("[${plugin.id}] send failed: ${error.message}")
                }
            }
        }
    }
    
    private fun buildSessionKey(inbound: agent.sdk.InboundMessage): String {
        return if (inbound.isGroup) {
            "agent:${config.agent.id}:${inbound.channelId}:group:${inbound.chatId}"
        } else {
            "agent:${config.agent.id}:main"
        }
    }
}

sealed class InitializationResult {
    data object NoPluginsLoaded : InitializationResult()
    data class Success(val count: Int) : InitializationResult()
}
