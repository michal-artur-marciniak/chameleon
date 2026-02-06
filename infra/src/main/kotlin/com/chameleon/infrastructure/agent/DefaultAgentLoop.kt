package com.chameleon.infrastructure.agent

import com.chameleon.agent.AgentEvent
import com.chameleon.agent.AgentLoopPort
import com.chameleon.agent.AgentRunRequest
import com.chameleon.agent.application.AgentRunService
import com.chameleon.config.domain.PlatformConfig
import com.chameleon.infrastructure.logging.LogWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import org.slf4j.LoggerFactory

/**
 * Infrastructure adapter that delegates to the application service.
 */
class DefaultAgentLoop(
    private val config: PlatformConfig,
    private val service: AgentRunService
) : AgentLoopPort {
    private val logger = LoggerFactory.getLogger(DefaultAgentLoop::class.java)
    private val stacktrace = config.logging.stacktrace

    override fun run(request: AgentRunRequest): Flow<AgentEvent> = service.run(request).catch { e ->
        LogWrapper.error(logger, "[agent] loop error", e, stacktrace)
        throw e
    }
}
