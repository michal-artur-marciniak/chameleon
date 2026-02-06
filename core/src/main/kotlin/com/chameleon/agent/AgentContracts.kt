package com.chameleon.agent

import com.chameleon.session.domain.Session
import com.chameleon.tool.port.ToolDefinitionRegistry
import kotlinx.coroutines.flow.Flow

interface AgentRuntime {
    fun start(request: AgentRunRequest): AgentRunHandle
    suspend fun wait(request: AgentWaitRequest): AgentRunResult
}

interface AgentLoopPort {
    fun run(request: AgentRunRequest): Flow<AgentEvent>
}

interface ContextAssembler {
    fun build(
        session: Session,
        tools: ToolDefinitionRegistry
    ): ContextBundle
}
