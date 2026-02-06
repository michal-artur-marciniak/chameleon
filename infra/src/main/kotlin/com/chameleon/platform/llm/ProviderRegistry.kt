package com.chameleon.llm

import com.chameleon.llm.port.LlmProviderPort
import com.chameleon.llm.port.LlmProviderRepositoryPort

class ProviderRegistry(
    private val providers: Map<String, LlmProviderPort>
) : LlmProviderRepositoryPort {
    override fun get(providerId: String): LlmProviderPort? = providers[providerId]

    override fun list(): Set<String> = providers.keys
}
