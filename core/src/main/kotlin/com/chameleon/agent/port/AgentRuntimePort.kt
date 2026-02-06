package com.chameleon.agent.port

import com.chameleon.agent.AgentRunHandle
import com.chameleon.agent.AgentRunRequest
import com.chameleon.agent.AgentRunResult
import com.chameleon.agent.AgentWaitRequest

interface AgentRuntimePort {
    fun start(request: AgentRunRequest): AgentRunHandle
    suspend fun wait(request: AgentWaitRequest): AgentRunResult
}
