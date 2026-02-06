package com.chameleon.application

import com.chameleon.agent.AgentEvent
import com.chameleon.agent.AgentLoop
import com.chameleon.agent.AgentRunRequest
import kotlinx.coroutines.flow.Flow

/**
 * Service that delegates agent runs to [AgentTurnService].
 *
 * Implements [AgentLoop] to provide a simple entry point for starting agent runs
 * while the actual turn-by-turn logic is handled by the underlying service.
 *
 * @property agentTurnService The service that handles the actual agent turn execution
 */
class AgentRunService(
    private val agentTurnService: AgentTurnService
) : AgentLoop {

    /**
     * Delegates the agent run to [agentTurnService].
     *
     * @param request The run request containing session key, agent ID, and inbound message
     * @return Flow of agent events (assistant deltas, tool events, lifecycle events)
     */
    override fun run(request: AgentRunRequest): Flow<AgentEvent> {
        return agentTurnService.run(request)
    }
}
