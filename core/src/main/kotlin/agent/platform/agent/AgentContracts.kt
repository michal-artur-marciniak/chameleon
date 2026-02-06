package agent.platform.agent

import agent.platform.session.domain.Session
import agent.platform.tool.port.ToolDefinitionRegistry
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
        session: Session,
        tools: ToolDefinitionRegistry
    ): ContextBundle
}
