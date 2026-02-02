package agent.platform.plugins

import agent.platform.config.PlatformConfig
import agent.platform.config.TelegramConfig
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PluginService(
    private val config: PlatformConfig,
    private val configPath: Path?,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val pluginsDir: Path = configPath?.parent?.resolve("plugins") ?: Paths.get("plugins")
    private val workspace = Paths.get(config.agent.workspace)
    private val indexStore = SessionIndexStore(workspace)

    // Official plugins registry - id -> factory function
    private val officialPlugins: Map<String, (PlatformConfig) -> ChannelPort?> = mapOf(
        "telegram" to { cfg ->
            val telegramConfig = cfg.channels.telegram
            if (!telegramConfig.enabled) return@to null
            val token = telegramConfig.token
            if (token.isNullOrBlank()) {
                println("[plugin-service] telegram: enabled but token missing")
                return@to null
            }
            TelegramPlugin(token = token, requireMentionInGroups = true)
        }
    )

    fun startAll() {
        val plugins = loadAllPlugins()

        if (plugins.isEmpty()) {
            println("[app] no plugins loaded")
            return
        }

        plugins.forEach { startPlugin(it) }
    }

    private fun loadAllPlugins(): List<ChannelPort> {
        val plugins = mutableListOf<ChannelPort>()

        // Load official plugins first
        officialPlugins.forEach { (id, factory) ->
            runCatching {
                factory(config)?.let {
                    println("[plugin-service] loaded official plugin: $id")
                    plugins.add(it)
                }
            }.onFailure { e ->
                println("[plugin-service] failed to load official plugin $id: ${e.message}")
            }
        }

        // Load external plugins from plugins directory
        loadExternalPlugins(plugins)

        return plugins
    }

    private fun loadExternalPlugins(plugins: MutableList<ChannelPort>) {
        if (!Files.exists(pluginsDir) || !Files.isDirectory(pluginsDir)) {
            return
        }

        Files.list(pluginsDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .forEach { pluginDir ->
                    loadExternalPlugin(pluginDir)?.let {
                        plugins.add(it)
                    }
                }
        }
    }

    private fun loadExternalPlugin(pluginDir: Path): ChannelPort? {
        val manifestPath = pluginDir.resolve("plugin.json")
        val jarPath = pluginDir.resolve("plugin.jar")

        // Check if this is an external plugin (has both manifest and JAR)
        if (!Files.exists(manifestPath) || !Files.exists(jarPath)) {
            return null
        }

        val manifest = loadManifest(manifestPath) ?: run {
            println("[plugin-service] failed to parse manifest: $manifestPath")
            return null
        }

        // Skip if an official plugin with same ID already loaded
        if (officialPlugins.containsKey(manifest.id)) {
            println("[plugin-service] skipping external plugin '${manifest.id}' - official plugin exists")
            return null
        }

        return instantiateExternalPlugin(manifest, jarPath, pluginDir)
    }

    private fun loadManifest(path: Path): PluginManifest? {
        if (!Files.exists(path)) return null
        return runCatching {
            json.decodeFromString(PluginManifest.serializer(), Files.readString(path))
        }.getOrNull()
    }

    private fun instantiateExternalPlugin(manifest: PluginManifest, jarPath: Path, pluginDir: Path): ChannelPort? {
        return runCatching {
            // Create URLClassLoader with the plugin JAR
            val classLoader = URLClassLoader(
                arrayOf(jarPath.toUri().toURL()),
                this::class.java.classLoader
            )

            // Load the plugin class
            val pluginClass = classLoader.loadClass(manifest.entryPoint)

            // Find appropriate constructor
            // Try constructors in order: (PluginManifest), (PlatformConfig), (Map), no-args
            val instance = when {
                // Constructor with PluginManifest
                pluginClass.constructors.any { it.parameterCount == 1 && it.parameterTypes[0] == PluginManifest::class.java } -> {
                    pluginClass.getConstructor(PluginManifest::class.java).newInstance(manifest)
                }
                // Constructor with PlatformConfig
                pluginClass.constructors.any { it.parameterCount == 1 && it.parameterTypes[0] == PlatformConfig::class.java } -> {
                    pluginClass.getConstructor(PlatformConfig::class.java).newInstance(config)
                }
                // Constructor with JsonObject for raw config
                pluginClass.constructors.any { it.parameterCount == 1 && it.parameterTypes[0] == JsonObject::class.java } -> {
                    val pluginConfig = extractPluginConfig(manifest.id)
                    pluginClass.getConstructor(JsonObject::class.java).newInstance(pluginConfig)
                }
                // No-args constructor
                else -> {
                    pluginClass.getConstructor().newInstance()
                }
            }

            // Verify it implements ChannelPort
            if (instance !is ChannelPort) {
                println("[plugin-service] plugin class does not implement ChannelPort: ${manifest.entryPoint}")
                return null
            }

            // Verify ID matches manifest
            if (instance.id != manifest.id) {
                println("[plugin-service] warning: plugin ID mismatch. Manifest: ${manifest.id}, Instance: ${instance.id}")
            }

            println("[plugin-service] loaded external plugin: ${manifest.id} v${manifest.version}")
            instance

        }.getOrElse { e ->
            println("[plugin-service] failed to load external plugin ${manifest.id}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun extractPluginConfig(pluginId: String): JsonObject {
        // Extract the plugin's config section from platform config
        // This is a simplified version - in practice you might want to serialize the config properly
        return json.parseToJsonElement(json.encodeToString(PlatformConfig.serializer(), config)).jsonObject
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
