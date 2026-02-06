package com.chameleon.plugin.domain

/**
 * Domain Events for the Plugin Context
 */
sealed class PluginEvent {
    abstract val pluginId: String
}

data class PluginDiscovered(
    override val pluginId: String,
    val version: String,
    val source: PluginSource
) : PluginEvent()

data class PluginLoaded(
    override val pluginId: String,
    val version: String,
    val source: PluginSource
) : PluginEvent()

data class PluginEnabled(
    override val pluginId: String
) : PluginEvent()

data class PluginDisabled(
    override val pluginId: String
) : PluginEvent()

data class PluginReloaded(
    override val pluginId: String
) : PluginEvent()

data class PluginError(
    override val pluginId: String,
    val message: String
) : PluginEvent()

data class PluginSkipped(
    override val pluginId: String,
    val reason: String
) : PluginEvent()
