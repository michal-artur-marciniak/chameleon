package com.chameleon.application

import com.chameleon.agent.AgentEvent
import com.chameleon.agent.AgentLoop
import com.chameleon.agent.AgentRunRequest
import kotlinx.coroutines.flow.Flow

class AgentRunService(
    private val agentTurnService: AgentTurnService
) : AgentLoop {
    override fun run(request: AgentRunRequest): Flow<AgentEvent> {
        return agentTurnService.run(request)
    }
}
