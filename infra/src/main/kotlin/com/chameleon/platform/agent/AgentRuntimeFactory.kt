package com.chameleon.agent

import com.chameleon.agent.port.DomainEventPublisherPort
import com.chameleon.application.AgentRunService
import com.chameleon.application.AgentTurnService
import com.chameleon.application.SessionAppService
import com.chameleon.application.ToolExecutionService
import com.chameleon.application.memory.MemoryContextAssembler
import com.chameleon.config.PlatformConfig
import com.chameleon.llm.ModelRefResolver
import com.chameleon.llm.OpenAiCompatProvider
import com.chameleon.llm.ProviderRegistry
import com.chameleon.memory.domain.MemoryIndex
import com.chameleon.memory.domain.MemorySearchService
import com.chameleon.memory.SqliteMemoryIndexAdapter
import com.chameleon.persistence.SessionFileRepository
import com.chameleon.session.DefaultSessionManager
import com.chameleon.tool.InMemoryToolDefinitionRegistry
import com.chameleon.tool.ToolExecutorAdapter
import com.chameleon.tool.ToolPolicyEvaluatorAdapter
import java.nio.file.Files
import java.nio.file.Paths

object AgentRuntimeFactory {
    fun create(
        config: PlatformConfig,
        eventPublisher: DomainEventPublisherPort? = null
    ): AgentRuntime {
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

    private fun createMemoryIndex(config: PlatformConfig): MemoryIndex {
        val dataDir = Paths.get(config.agents.defaults.workspace).resolve("data")
        Files.createDirectories(dataDir)
        val dbPath = dataDir.resolve("memory.sqlite")
        val adapter = SqliteMemoryIndexAdapter(dbPath)
        return MemoryIndex.create(repository = adapter)
    }
}
