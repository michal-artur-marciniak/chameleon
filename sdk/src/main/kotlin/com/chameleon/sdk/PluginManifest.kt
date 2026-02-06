package com.chameleon.sdk

import kotlinx.serialization.Serializable

@Serializable
data class PluginManifest(
    val id: String,
    val version: String,
    val entryPoint: String,
    val type: String
)
