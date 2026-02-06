package com.chameleon.agent.port

import com.chameleon.agent.application.AgentRunHandle
import com.chameleon.agent.application.AgentRunRequest
import com.chameleon.agent.application.AgentRunResult
import com.chameleon.agent.application.AgentWaitRequest

interface AgentRuntimePort {
    fun start(request: AgentRunRequest): AgentRunHandle
    suspend fun wait(request: AgentWaitRequest): AgentRunResult
}
