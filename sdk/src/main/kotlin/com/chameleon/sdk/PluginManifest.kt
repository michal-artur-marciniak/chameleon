package com.chameleon.sdk

import kotlinx.serialization.Serializable

/**
 * Plugin manifest that describes a plugin's metadata and entry point.
 * Loaded from plugin.json in each plugin's root directory.
 *
 * @property id Unique plugin identifier (must be unique across all plugins)
 * @property version Semantic version of the plugin
 * @property entryPoint Fully qualified class name implementing the plugin interface
 * @property type Plugin category: "channel" for channel integrations
 */
@Serializable
data class PluginManifest(
    val id: String,
    val version: String,
    val entryPoint: String,
    val type: String
)
