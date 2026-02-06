package agent.platform.llm.port

interface LlmProviderRepositoryPort {
    fun get(providerId: String): LlmProviderPort?
    fun list(): Set<String>
}
