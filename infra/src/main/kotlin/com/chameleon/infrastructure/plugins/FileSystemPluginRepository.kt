package com.chameleon.plugins

import com.chameleon.config.domain.PlatformConfig
import com.chameleon.plugin.domain.PluginId
import com.chameleon.plugin.port.PluginRepository
import com.chameleon.sdk.ChannelPort
import com.chameleon.sdk.PluginManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.chameleon.logging.LogWrapper
import org.slf4j.LoggerFactory
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Repository for loading external plugins from filesystem.
 *
 * Expects structure:
 * ```
 * extensions/
 *   my-plugin/
 *     plugin.json   (manifest)
 *     plugin.jar    (implementation JAR)
 * ```
 *
 * Supports constructor injection of PluginManifest, PlatformConfig, or JsonObject.
 */
class FileSystemPluginRepository(
    private val extensionsDir: Path,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PluginRepository {
    private val logger = LoggerFactory.getLogger(FileSystemPluginRepository::class.java)
    private val stacktrace = System.getProperty("LOG_STACKTRACE")?.toBoolean() == true
    
    override fun discover(): List<PluginManifest> {
        if (!Files.exists(extensionsDir) || !Files.isDirectory(extensionsDir)) {
            logger.info("[plugin-repository] extensions dir missing: {}", extensionsDir)
            return emptyList()
        }
        
        val manifests = mutableListOf<PluginManifest>()
        
        Files.list(extensionsDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .forEach { pluginDir ->
                    val manifestPath = pluginDir.resolve("plugin.json")
                    val jarPath = pluginDir.resolve("plugin.jar")
                    
                    if (Files.exists(manifestPath) && Files.exists(jarPath)) {
                        logger.info("[plugin-repository] discovered: {}", pluginDir.fileName)
                        loadManifest(manifestPath)?.let { manifests.add(it) }
                    } else {
                        logger.info(
                            "[plugin-repository] skipping {}: manifest={} jar={}",
                            pluginDir.fileName,
                            Files.exists(manifestPath),
                            Files.exists(jarPath)
                        )
                    }
                }
        }
        
        return manifests
    }
    
    override fun load(manifest: PluginManifest, config: PlatformConfig): ChannelPort? {
        val pluginDir = extensionsDir.resolve(manifest.id)
        val jarPath = pluginDir.resolve("plugin.jar")
        
        if (!Files.exists(jarPath)) {
            logger.warn("[plugin-repository] JAR not found: {}", jarPath)
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
            LogWrapper.warn(
                logger,
                "[plugin-repository] failed to parse manifest: $path",
                e,
                stacktrace
            )
            null
        }
    }
    
    private fun instantiatePlugin(
        manifest: PluginManifest, 
        jarPath: Path, 
        config: PlatformConfig
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
                    val configJson = extractConfigJson(config)
                    pluginClass.getConstructor(JsonObject::class.java)
                        .newInstance(configJson)
                }
                // No-args constructor
                else -> {
                    pluginClass.getConstructor().newInstance()
                }
            }
            
            if (instance !is ChannelPort) {
                logger.warn("[plugin-repository] class does not implement ChannelPort: {}", manifest.entryPoint)
                return null
            }
            
            if (instance.id != manifest.id) {
                logger.warn(
                    "[plugin-repository] warning: ID mismatch. Manifest: {}, Instance: {}",
                    manifest.id,
                    instance.id
                )
            }
            
            instance
            
        } catch (e: Exception) {
            LogWrapper.error(
                logger,
                "[plugin-repository] failed to instantiate plugin ${manifest.id}",
                e,
                stacktrace
            )
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
