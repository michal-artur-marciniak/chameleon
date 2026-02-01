package agent.platform.plugins

import agent.sdk.PluginManifest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class PluginLoader(
    private val pluginsDir: Path,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun loadBuiltins(): List<PluginManifest> {
        val telegramManifest = pluginsDir.resolve("telegram/plugin.json")
        return listOfNotNull(loadManifest(telegramManifest))
    }

    private fun loadManifest(path: Path): PluginManifest? {
        if (!Files.exists(path)) return null
        val content = Files.readString(path)
        return json.decodeFromString(PluginManifest.serializer(), content)
    }
}
