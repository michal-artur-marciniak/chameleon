package agent.platform

import agent.platform.config.PlatformConfig
import agent.platform.persistence.SessionIndexStore
import agent.platform.plugins.ChannelConfig
import agent.platform.plugins.PluginLoader
import agent.sdk.ChannelPort
import agent.sdk.OutboundMessage
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun main() {
    val configPath = resolveConfigPath()
    val envPath = resolveEnvPath(configPath)
    val env = loadDotEnv(envPath)
    val config = loadConfig(configPath, env)
    logStartup(config, configPath, envPath)

    val server = embeddedServer(Netty, host = config.gateway.host, port = config.gateway.port) {
        install(WebSockets)
        routing {
            get("/health") { call.respondText("ok") }
            webSocket("/ws") {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        send(Frame.Text(frame.readText()))
                    }
                }
            }
        }
    }

    // Load and start enabled plugins
    val pluginsDir = configPath?.parent?.resolve("plugins") ?: Paths.get("plugins")
    val pluginLoader = PluginLoader(pluginsDir)
    val channelConfigs = mapOf(
        "telegram" to ChannelConfig(
            enabled = config.channels.telegram.enabled,
            token = config.channels.telegram.token
        )
    )
    val loadedPlugins = pluginLoader.loadEnabledPlugins(channelConfigs)

    if (loadedPlugins.isEmpty()) {
        println("[app] no plugins loaded")
    } else {
        loadedPlugins.forEach { loaded ->
            startPluginEcho(config, loaded.plugin)
        }
    }

    server.start(wait = true)
}

private fun startPluginEcho(config: PlatformConfig, plugin: ChannelPort) {
    val workspace = Paths.get(config.agent.workspace)
    val indexStore = SessionIndexStore(workspace)
    val handler = CoroutineExceptionHandler { _, throwable ->
        println("[${plugin.id}] coroutine error: ${throwable.message}")
    }
    CoroutineScope(Dispatchers.IO + handler).launch {
        println("[${plugin.id}] starting")
        plugin.start { inbound ->
            val sessionKey = buildSessionKey(config.agent.id, inbound.channelId, inbound.chatId, inbound.isGroup)
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

private fun buildSessionKey(agentId: String, channelId: String, chatId: String, isGroup: Boolean): String {
    return if (isGroup) {
        "agent:$agentId:$channelId:group:$chatId"
    } else {
        "agent:$agentId:main"
    }
}

private fun loadConfig(path: Path?, env: Map<String, String>): PlatformConfig {
    val json = Json { ignoreUnknownKeys = true }
    if (path == null || !Files.exists(path)) return PlatformConfig()
    val content = Files.readString(path)
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

private fun logStartup(config: PlatformConfig, configPath: Path?, envPath: Path) {
    val telegramEnabled = config.channels.telegram.enabled
    val tokenPresent = !config.channels.telegram.token.isNullOrBlank()
    val configLog = configPath?.toAbsolutePath()?.toString() ?: "<not found>"
    println("[app] config=$configLog")
    println("[app] env=${envPath.toAbsolutePath()}")
    println("[app] gateway=${config.gateway.host}:${config.gateway.port}")
    println("[app] telegram enabled=$telegramEnabled token_present=$tokenPresent")
    if (configPath == null) {
        println("[app] warning: config/config.json not found; using defaults")
    }
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
