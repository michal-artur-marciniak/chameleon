package agent.platform.llm

data class ModelRef(
    val providerId: String,
    val modelId: String
)

class ModelRefResolver(
    private val providers: Set<String>
) {
    fun resolve(modelRef: String): ModelRef {
        val trimmed = modelRef.trim()
        require(trimmed.isNotEmpty()) { "Model ref cannot be blank" }

        val slashIndex = trimmed.indexOf('/')
        if (slashIndex > 0) {
            val provider = trimmed.substring(0, slashIndex)
            val modelId = trimmed.substring(slashIndex + 1)
            require(modelId.isNotBlank()) { "Model ref missing model id: $modelRef" }
            require(providers.contains(provider)) {
                "Unknown provider '$provider'. Configured: ${providers.sorted()}"
            }
            return ModelRef(provider, modelId)
        }

        if (providers.isEmpty()) {
            throw IllegalStateException("No LLM providers configured")
        }

        if (providers.size == 1) {
            return ModelRef(providers.first(), trimmed)
        }

        throw IllegalStateException(
            "Model ref '$modelRef' is missing provider prefix. Use provider/model."
        )
    }
}
