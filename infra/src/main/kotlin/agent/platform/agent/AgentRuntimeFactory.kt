package agent.platform.agent

import agent.platform.agent.ports.DomainEventPublisherPort
import agent.platform.application.AgentRunService
import agent.platform.application.AgentTurnService
import agent.platform.application.SessionAppService
import agent.platform.application.ToolExecutionService
import agent.platform.application.memory.MemoryContextAssembler
import agent.platform.config.PlatformConfig
import agent.platform.llm.ModelRefResolver
import agent.platform.llm.OpenAiCompatProvider
import agent.platform.llm.ProviderRegistry
import agent.platform.memory.MemoryIndex
import agent.platform.memory.MemorySearchService
import agent.platform.persistence.SessionFileRepository
import agent.platform.session.DefaultSessionManager
import agent.platform.tool.InMemoryToolDefinitionRegistry
import agent.platform.tool.ToolExecutorAdapter
import agent.platform.tool.ToolPolicyEvaluatorAdapter
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
        val adapter = agent.platform.memory.SqliteMemoryIndexAdapter(dbPath)
        return MemoryIndex.create(repository = adapter)
    }
}
