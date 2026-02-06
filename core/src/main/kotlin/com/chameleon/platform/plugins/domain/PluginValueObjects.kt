package com.chameleon.plugins.domain

/**
 * Value Object - Unique plugin identifier
 */
@JvmInline
value class PluginId(val value: String) {
    init {
        require(value.isNotBlank()) { "Plugin ID cannot be blank" }
        require(value.matches(Regex("^[a-z][a-z0-9_-]*$"))) { 
            "Plugin ID must be lowercase, start with letter, contain only a-z, 0-9, _, -" 
        }
    }
}

/**
 * Value Object - Semantic version
 */
data class PluginVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null
) {
    companion object {
        fun parse(version: String): PluginVersion {
            val parts = version.split("-", limit = 2)
            val versionParts = parts[0].split(".")
            
            require(versionParts.size == 3) { "Version must be in format MAJOR.MINOR.PATCH" }
            
            return PluginVersion(
                major = versionParts[0].toInt(),
                minor = versionParts[1].toInt(),
                patch = versionParts[2].toInt(),
                prerelease = parts.getOrNull(1)
            )
        }
    }
    
    override fun toString(): String = 
        "$major.$minor.$patch${prerelease?.let { "-$it" } ?: ""}"
}

/**
 * Value Object - Plugin type classification
 */
enum class PluginType {
    CHANNEL,    // Messaging integration (Telegram, Discord, etc.)
    LLM,        // LLM provider
    TOOL,       // Tool extension
    UNKNOWN;
    
    companion object {
        fun fromString(type: String): PluginType = 
            entries.find { it.name.equals(type, ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Value Object - What capabilities a plugin provides
 */
enum class PluginCapability {
    RECEIVE_MESSAGES,   // Can receive inbound messages
    SEND_MESSAGES,      // Can send outbound messages
    PROVIDE_LLM,        // Can provide LLM completions
    EXECUTE_TOOL,       // Can execute tools
    REGISTER_TOOL       // Can register new tools
}

/**
 * Value Object - Plugin source classification
 */
enum class PluginSource {
    OFFICIAL,   // Bundled with the application
    EXTERNAL    // Loaded from external JAR
}

/**
 * Value Object - Plugin lifecycle status
 */
enum class PluginStatus {
    DISCOVERED,     // Found but not loaded
    LOADED,         // Loaded but not enabled
    ENABLED,        // Active and running
    DISABLED,       // Loaded but disabled
    ERROR           // Failed to load
}
