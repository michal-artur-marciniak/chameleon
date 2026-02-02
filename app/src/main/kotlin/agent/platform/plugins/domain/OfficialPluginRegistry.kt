package agent.platform.plugins.domain

import agent.platform.config.PlatformConfig
import agent.platform.config.TelegramConfig
import agent.plugin.telegram.TelegramPlugin
import agent.sdk.ChannelPort

/**
 * Registry for official (built-in) plugins
 */
class OfficialPluginRegistry {
    
    private val factories = mutableMapOf<PluginId, PluginFactory>()
    
    init {
        // Register built-in plugins
        register(telegramFactory())
    }
    
    private fun register(factory: PluginFactory) {
        factories[factory.id] = factory
    }
    
    fun discover(config: PlatformConfig): List<PluginFactory> {
        return factories.values.filter { it.isEnabled(config) }
    }
    
    fun getFactory(id: PluginId): PluginFactory? = factories[id]
    
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
            
            return TelegramPlugin(token = token, requireMentionInGroups = true)
        }
    }
}

/**
 * Factory interface for creating official plugin instances
 */
interface PluginFactory {
    val id: PluginId
    val version: PluginVersion
    val type: PluginType
    val capabilities: Set<PluginCapability>
    
    fun isEnabled(config: PlatformConfig): Boolean
    fun create(config: PlatformConfig): ChannelPort?
}
