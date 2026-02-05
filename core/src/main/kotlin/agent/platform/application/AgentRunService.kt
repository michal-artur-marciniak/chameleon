package agent.platform.application

import agent.platform.agent.AgentEvent
import agent.platform.agent.AgentLoop
import agent.platform.agent.AgentRunRequest
import kotlinx.coroutines.flow.Flow

class AgentRunService(
    private val agentTurnService: AgentTurnService
) : AgentLoop {
    override fun run(request: AgentRunRequest): Flow<AgentEvent> {
        return agentTurnService.run(request)
    }
}
