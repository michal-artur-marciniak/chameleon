package agent.platform.llm.ports

interface LlmProviderRepositoryPort {
    fun get(providerId: String): LlmProviderPort?
    fun list(): Set<String>
}
