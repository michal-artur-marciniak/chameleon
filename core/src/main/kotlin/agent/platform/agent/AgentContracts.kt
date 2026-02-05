package agent.platform.agent

import kotlinx.coroutines.flow.Flow

interface AgentRuntime {
    fun start(request: AgentRunRequest): AgentRunHandle
    suspend fun wait(request: AgentWaitRequest): AgentRunResult
}

interface AgentLoop {
    fun run(request: AgentRunRequest): Flow<AgentEvent>
}

interface ContextAssembler {
    fun build(
        session: agent.platform.session.Session,
        tools: agent.platform.tool.ToolDefinitionRegistry
    ): ContextBundle
}
