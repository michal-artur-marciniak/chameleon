package agent.platform.plugins

import agent.platform.config.PlatformConfig
import agent.platform.plugins.domain.PluginId
import agent.platform.plugins.domain.PluginRepository
import agent.sdk.ChannelPort
import agent.sdk.PluginManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Repository for loading external plugins from filesystem
 * 
 * Expects structure:
 *   extensions/
 *     my-plugin/
 *       plugin.json   (manifest)
 *       plugin.jar    (implementation JAR)
 */
class FileSystemPluginRepository(
    private val extensionsDir: Path,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PluginRepository {
    
    override fun discover(): List<PluginManifest> {
        if (!Files.exists(extensionsDir) || !Files.isDirectory(extensionsDir)) {
            println("[plugin-repository] extensions dir missing: $extensionsDir")
            return emptyList()
        }
        
        val manifests = mutableListOf<PluginManifest>()
        
        Files.list(extensionsDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .forEach { pluginDir ->
                    val manifestPath = pluginDir.resolve("plugin.json")
                    val jarPath = pluginDir.resolve("plugin.jar")
                    
                    if (Files.exists(manifestPath) && Files.exists(jarPath)) {
                        println("[plugin-repository] discovered: ${pluginDir.fileName}")
                        loadManifest(manifestPath)?.let { manifests.add(it) }
                    } else {
                        println(
                            "[plugin-repository] skipping ${pluginDir.fileName}: " +
                                "manifest=${Files.exists(manifestPath)} jar=${Files.exists(jarPath)}"
                        )
                    }
                }
        }
        
        return manifests
    }
    
    override fun load(manifest: PluginManifest, config: Any?): ChannelPort? {
        val pluginDir = extensionsDir.resolve(manifest.id)
        val jarPath = pluginDir.resolve("plugin.jar")
        
        if (!Files.exists(jarPath)) {
            println("[plugin-repository] JAR not found: $jarPath")
            return null
        }
        
        return instantiatePlugin(manifest, jarPath, config)
    }
    
    override fun findManifest(id: PluginId): PluginManifest? {
        val pluginDir = extensionsDir.resolve(id.value)
        val manifestPath = pluginDir.resolve("plugin.json")
        
        return if (Files.exists(manifestPath)) {
            loadManifest(manifestPath)
        } else null
    }
    
    private fun loadManifest(path: Path): PluginManifest? {
        return try {
            json.decodeFromString(
                PluginManifest.serializer(), 
                Files.readString(path)
            )
        } catch (e: Exception) {
            println("[plugin-repository] failed to parse manifest: $path - ${e.message}")
            null
        }
    }
    
    private fun instantiatePlugin(
        manifest: PluginManifest, 
        jarPath: Path, 
        config: Any?
    ): ChannelPort? {
        return try {
            val classLoader = URLClassLoader(
                arrayOf(jarPath.toUri().toURL()),
                this::class.java.classLoader
            )
            
            val pluginClass = classLoader.loadClass(manifest.entryPoint)
            
            // Try constructors in order of preference
            val instance = when {
                // Constructor with PluginManifest
                hasConstructor(pluginClass, PluginManifest::class.java) -> {
                    pluginClass.getConstructor(PluginManifest::class.java)
                        .newInstance(manifest)
                }
                // Constructor with PlatformConfig
                hasConstructor(pluginClass, PlatformConfig::class.java) -> {
                    pluginClass.getConstructor(PlatformConfig::class.java)
                        .newInstance(config)
                }
                // Constructor with JsonObject
                hasConstructor(pluginClass, JsonObject::class.java) -> {
                    val configJson = when (config) {
                        is PlatformConfig -> extractConfigJson(config)
                        is JsonObject -> config
                        else -> JsonObject(emptyMap())
                    }
                    pluginClass.getConstructor(JsonObject::class.java)
                        .newInstance(configJson)
                }
                // No-args constructor
                else -> {
                    pluginClass.getConstructor().newInstance()
                }
            }
            
            if (instance !is ChannelPort) {
                println("[plugin-repository] class does not implement ChannelPort: ${manifest.entryPoint}")
                return null
            }
            
            if (instance.id != manifest.id) {
                println("[plugin-repository] warning: ID mismatch. Manifest: ${manifest.id}, Instance: ${instance.id}")
            }
            
            instance
            
        } catch (e: Exception) {
            println("[plugin-repository] failed to instantiate plugin ${manifest.id}: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun hasConstructor(clazz: Class<*>, vararg paramTypes: Class<*>): Boolean {
        return clazz.constructors.any { 
            it.parameterCount == paramTypes.size && 
            it.parameterTypes.contentEquals(paramTypes) 
        }
    }
    
    private fun extractConfigJson(config: PlatformConfig): JsonObject {
        return json.parseToJsonElement(
            json.encodeToString(PlatformConfig.serializer(), config)
        ).jsonObject
    }
}
