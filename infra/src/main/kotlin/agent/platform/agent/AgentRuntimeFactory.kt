package agent.platform.agent

import agent.platform.config.PlatformConfig
import agent.platform.llm.StubLlmProvider
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
        val llmProvider = StubLlmProvider()
        val loop = DefaultAgentLoop(
            config = config,
            sessionManager = sessionManager,
            contextAssembler = contextAssembler,
            toolRegistry = toolRegistry,
            llmProvider = llmProvider
        )
        return DefaultAgentRuntime(config, loop)
    }
}
