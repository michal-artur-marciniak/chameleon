package agent.platform.agent

import agent.platform.config.PlatformConfig
import agent.platform.llm.ModelRefResolver
import agent.platform.llm.OpenAiCompatProvider
import agent.platform.llm.ProviderRegistry
import agent.platform.persistence.SessionFileRepository
import agent.platform.session.DefaultSessionManager
import agent.platform.tool.InMemoryToolRegistry
import java.nio.file.Paths

object AgentRuntimeFactory {
    fun create(config: PlatformConfig): AgentRuntime {
        val sessionRepo = SessionFileRepository(Paths.get(config.agents.defaults.workspace))
        val sessionManager = DefaultSessionManager(sessionRepo)
        val toolRegistry = InMemoryToolRegistry()
        val contextAssembler = DefaultContextAssembler(config)
        val registry = buildProviders(config)
        val resolver = ModelRefResolver(registry.ids())
        val loop = DefaultAgentLoop(
            config = config,
            sessionManager = sessionManager,
            contextAssembler = contextAssembler,
            toolRegistry = toolRegistry,
            providerRegistry = registry,
            modelRefResolver = resolver
        )
        return DefaultAgentRuntime(config, loop)
    }

    private fun buildProviders(config: PlatformConfig): ProviderRegistry {
        val providers = config.models.providers.mapValues { (_, providerConfig) ->
            OpenAiCompatProvider(
                baseUrl = providerConfig.baseUrl.trimEnd('/'),
                apiKey = providerConfig.apiKey,
                extraHeaders = providerConfig.headers
            )
        }
        return ProviderRegistry(providers)
    }
}
