package agent.platform.llm

import agent.platform.llm.port.LlmProviderPort
import agent.platform.llm.port.LlmProviderRepositoryPort

class ProviderRegistry(
    private val providers: Map<String, LlmProviderPort>
) : LlmProviderRepositoryPort {
    override fun get(providerId: String): LlmProviderPort? = providers[providerId]

    override fun list(): Set<String> = providers.keys
}
