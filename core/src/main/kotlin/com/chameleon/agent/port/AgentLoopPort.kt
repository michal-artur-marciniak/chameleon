package com.chameleon.agent.port

import com.chameleon.agent.application.AgentEvent
import com.chameleon.agent.application.AgentRunRequest
import kotlinx.coroutines.flow.Flow

interface AgentLoopPort {
    fun run(request: AgentRunRequest): Flow<AgentEvent>
}
