package agent.platform.llm

import agent.platform.llm.ports.LlmProviderRepositoryPort

data class ModelRef(
    val providerId: String,
    val modelId: String
)

sealed interface ModelRefResolutionError {
    data class BlankModelRef(val message: String) : ModelRefResolutionError
    data class MissingModelId(val message: String) : ModelRefResolutionError
    data class NoProvidersConfigured(val message: String) : ModelRefResolutionError
    data class UnknownProvider(val providerId: String, val configured: Set<String>) : ModelRefResolutionError
    data class ProviderPrefixRequired(val message: String) : ModelRefResolutionError
}

data class ModelRefResolutionResult(
    val modelRef: ModelRef? = null,
    val error: ModelRefResolutionError? = null
) {
    val isSuccess: Boolean
        get() = modelRef != null && error == null
}


class ModelRefResolver(
    private val providerRepository: LlmProviderRepositoryPort
) {
    fun resolve(modelRef: String): ModelRefResolutionResult {
        val trimmed = modelRef.trim()
        if (trimmed.isEmpty()) {
            return ModelRefResolutionResult(
                error = ModelRefResolutionError.BlankModelRef("Model ref cannot be blank")
            )
        }

        val slashIndex = trimmed.indexOf('/')
        if (slashIndex > 0) {
            val provider = trimmed.substring(0, slashIndex)
            val modelId = trimmed.substring(slashIndex + 1)
            if (modelId.isBlank()) {
                return ModelRefResolutionResult(
                    error = ModelRefResolutionError.MissingModelId("Model ref missing model id: $modelRef")
                )
            }
            val configured = providerRepository.list()
            if (!configured.contains(provider)) {
                return ModelRefResolutionResult(
                    error = ModelRefResolutionError.UnknownProvider(provider, configured)
                )
            }
            return ModelRefResolutionResult(modelRef = ModelRef(provider, modelId))
        }

        val providers = providerRepository.list()
        if (providers.isEmpty()) {
            return ModelRefResolutionResult(
                error = ModelRefResolutionError.NoProvidersConfigured("No LLM providers configured")
            )
        }

        if (providers.size == 1) {
            return ModelRefResolutionResult(modelRef = ModelRef(providers.first(), trimmed))
        }

        return ModelRefResolutionResult(
            error = ModelRefResolutionError.ProviderPrefixRequired(
                "Model ref '$modelRef' is missing provider prefix. Use provider/model."
            )
        )
    }
}
