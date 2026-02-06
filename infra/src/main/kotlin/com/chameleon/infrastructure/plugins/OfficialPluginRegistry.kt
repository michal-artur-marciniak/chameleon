package com.chameleon.infrastructure.plugins

import com.chameleon.config.domain.PlatformConfig
import com.chameleon.plugin.domain.PluginCapability
import com.chameleon.plugin.domain.PluginFactory
import com.chameleon.plugin.domain.PluginFactoryRegistry
import com.chameleon.plugin.domain.PluginId
import com.chameleon.plugin.domain.PluginType
import com.chameleon.plugin.domain.PluginVersion
import com.chameleon.plugin.telegram.TelegramConfig
import com.chameleon.plugin.telegram.TelegramPlugin
import com.chameleon.sdk.ChannelPort
import kotlinx.serialization.json.Json

/**
 * Registry for official (built-in) plugins.
 *
 * Currently registers:
 * - Telegram plugin (when token is configured)
 */
class OfficialPluginRegistry : PluginFactoryRegistry {
    
    private val factories = mutableMapOf<PluginId, PluginFactory>()
    
    init {
        // Register built-in plugins
        register(telegramFactory())
    }
    
    private fun register(factory: PluginFactory) {
        factories[factory.id] = factory
    }
    
    override fun discover(config: PlatformConfig): List<PluginFactory> {
        return factories.values.filter { it.isEnabled(config) }
    }
    
    override fun getFactory(id: PluginId): PluginFactory? = factories[id]
    
    private fun telegramFactory(): PluginFactory = object : PluginFactory {
        override val id = PluginId("telegram")
        override val version = PluginVersion(1, 0, 0)
        override val type = PluginType.CHANNEL
        override val capabilities = setOf(
            PluginCapability.RECEIVE_MESSAGES,
            PluginCapability.SEND_MESSAGES
        )
        
        override fun isEnabled(config: PlatformConfig): Boolean {
            val telegramConfig = resolveTelegramConfig(config)
            return telegramConfig.enabled && !telegramConfig.token.isNullOrBlank()
        }
        
        override fun create(config: PlatformConfig): ChannelPort? {
            val telegramConfig = resolveTelegramConfig(config)
            if (!telegramConfig.enabled) return null

            val token = telegramConfig.token
            if (token.isNullOrBlank()) {
                println("[plugin-registry] telegram: enabled but token missing")
                return null
            }

            return TelegramPlugin(
                token = token,
                requireMentionInGroups = telegramConfig.requireMention
            )
        }
    }

    private fun resolveTelegramConfig(config: PlatformConfig): TelegramConfig {
        val json = Json { ignoreUnknownKeys = true }
        val raw = config.channels.get("telegram") ?: return TelegramConfig()
        return json.decodeFromJsonElement(TelegramConfig.serializer(), raw)
    }
}
