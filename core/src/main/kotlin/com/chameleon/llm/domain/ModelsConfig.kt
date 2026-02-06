package com.chameleon.llm.domain

import kotlinx.serialization.Serializable

@Serializable
data class ModelsConfig(
    val providers: Map<String, ModelProviderConfig> = emptyMap()
)

@Serializable
data class ModelProviderConfig(
    val baseUrl: String,
    val apiKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val models: List<ModelDefinitionConfig> = emptyList()
)

@Serializable
data class ModelDefinitionConfig(
    val id: String,
    val name: String,
    val contextWindow: Int,
    val maxTokens: Int,
    val reasoning: Boolean = false
)
