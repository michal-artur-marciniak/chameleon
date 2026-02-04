package agent.platform.llm

interface LlmProviderRepositoryPort {
    fun get(providerId: String): LlmProviderPort?
    fun list(): Set<String>
}
