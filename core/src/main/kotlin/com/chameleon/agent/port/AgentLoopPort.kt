package com.chameleon.agent.port

import com.chameleon.agent.AgentEvent
import com.chameleon.agent.AgentRunRequest
import kotlinx.coroutines.flow.Flow

interface AgentLoopPort {
    fun run(request: AgentRunRequest): Flow<AgentEvent>
}
