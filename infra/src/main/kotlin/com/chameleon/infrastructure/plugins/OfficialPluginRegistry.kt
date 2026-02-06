package com.chameleon.plugins

import com.chameleon.config.PlatformConfig
import com.chameleon.plugins.domain.PluginCapability
import com.chameleon.plugins.domain.PluginFactory
import com.chameleon.plugins.domain.PluginFactoryRegistry
import com.chameleon.plugins.domain.PluginId
import com.chameleon.plugins.domain.PluginType
import com.chameleon.plugins.domain.PluginVersion
import com.chameleon.plugin.telegram.TelegramPlugin
import com.chameleon.sdk.ChannelPort

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
            return config.channels.telegram.enabled && 
                   !config.channels.telegram.token.isNullOrBlank()
        }
        
        override fun create(config: PlatformConfig): ChannelPort? {
            val telegramConfig = config.channels.telegram
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
}
