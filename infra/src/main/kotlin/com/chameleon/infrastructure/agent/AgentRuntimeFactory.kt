package com.chameleon.infrastructure.agent

import com.chameleon.agent.port.AgentRuntimePort
import com.chameleon.agent.port.DomainEventPublisherPort
import com.chameleon.agent.application.AgentRunService
import com.chameleon.agent.application.AgentTurnService
import com.chameleon.memory.application.MemoryContextAssembler
import com.chameleon.session.application.SessionAppService
import com.chameleon.tool.application.ToolExecutionService
import com.chameleon.config.domain.PlatformConfig
import com.chameleon.llm.domain.ModelRefResolver
import com.chameleon.memory.domain.MemoryIndex
import com.chameleon.memory.domain.MemorySearchService
import com.chameleon.infrastructure.llm.OpenAiCompatProvider
import com.chameleon.infrastructure.llm.ProviderRegistry
import com.chameleon.infrastructure.memory.SqliteMemoryIndexAdapter
import com.chameleon.infrastructure.persistence.SessionFileRepository
import com.chameleon.infrastructure.session.DefaultSessionManager
import com.chameleon.infrastructure.tool.InMemoryToolDefinitionRegistry
import com.chameleon.infrastructure.tool.ToolExecutorAdapter
import com.chameleon.infrastructure.tool.ToolPolicyEvaluatorAdapter
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Factory for creating [AgentRuntimePort] instances with all dependencies wired.
 *
 * This factory creates a fully-configured agent runtime including:
 * - Session persistence and management
 * - Tool definition registry and execution
 * - LLM provider registry
 * - Memory indexing and search
 * - Domain event publishing
 */
object AgentRuntimeFactory {
    /**
     * Creates a new [AgentRuntimePort] with the given configuration.
     *
     * @param config Platform configuration including model providers, workspace paths
     * @param eventPublisher Optional domain event publisher for observability (null = no events)
     * @return Fully configured [AgentRuntimePort] ready to start agent runs
     */
    fun create(
        config: PlatformConfig,
        eventPublisher: DomainEventPublisherPort? = null
    ): AgentRuntimePort {
        val sessionRepo = SessionFileRepository(Paths.get(config.agents.defaults.workspace))
        val sessionManager = DefaultSessionManager(sessionRepo)
        val sessionAppService = SessionAppService(sessionManager)
        val toolDefinitionRegistry = InMemoryToolDefinitionRegistry()
        val toolPolicyEvaluator = ToolPolicyEvaluatorAdapter(toolDefinitionRegistry)
        val toolExecutor = ToolExecutorAdapter(toolDefinitionRegistry)
        val toolExecutionService = ToolExecutionService(
            toolDefinitionRegistry = toolDefinitionRegistry,
            toolPolicyEvaluator = toolPolicyEvaluator,
            toolExecutor = toolExecutor
        )
        val contextAssembler = DefaultContextAssembler(config)
        val memoryIndex = createMemoryIndex(config)
        val memorySearch = MemorySearchService()
        val memoryContextAssembler = MemoryContextAssembler(memoryIndex, memorySearch)
        val registry = buildProviders(config)
        val resolver = ModelRefResolver(registry)
        val turnService = AgentTurnService(
            config = config,
            sessionManager = sessionManager,
            sessionRepository = sessionRepo,
            sessionAppService = sessionAppService,
            contextAssembler = contextAssembler,
            toolDefinitionRegistry = toolDefinitionRegistry,
            toolExecutionService = toolExecutionService,
            providerRegistry = registry,
            modelRefResolver = resolver,
            eventPublisher = eventPublisher,
            memoryContextAssembler = memoryContextAssembler
        )
        val agentRunService = AgentRunService(turnService)
        val loop = DefaultAgentLoop(
            config = config,
            service = agentRunService
        )
        return DefaultAgentRuntime(config, loop)
    }

    /**
     * Builds a [ProviderRegistry] from the configured model providers.
     */
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

    /**
     * Creates a [MemoryIndex] backed by SQLite in the workspace data directory.
     */
    private fun createMemoryIndex(config: PlatformConfig): MemoryIndex {
        val dataDir = Paths.get(config.agents.defaults.workspace).resolve("data")
        Files.createDirectories(dataDir)
        val dbPath = dataDir.resolve("memory.sqlite")
        val adapter = SqliteMemoryIndexAdapter(dbPath)
        return MemoryIndex.create(repository = adapter)
    }
}
