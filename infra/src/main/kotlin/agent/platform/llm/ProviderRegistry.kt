package agent.platform.llm

import agent.platform.llm.LlmProviderPort

class ProviderRegistry(
    private val providers: Map<String, LlmProviderPort>
) {
    fun get(providerId: String): LlmProviderPort {
        return providers[providerId]
            ?: error("LLM provider not registered: $providerId")
    }

    fun ids(): Set<String> = providers.keys
}
