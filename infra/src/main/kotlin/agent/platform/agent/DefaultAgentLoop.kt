package agent.platform.agent

import agent.platform.agent.AgentEvent
import agent.platform.agent.AgentLoop
import agent.platform.agent.AgentRunRequest
import agent.platform.application.AgentRunService
import agent.platform.config.PlatformConfig
import agent.platform.logging.LogWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import org.slf4j.LoggerFactory

/**
 * Infrastructure adapter that delegates to the application service.
 */
class DefaultAgentLoop(
    private val config: PlatformConfig,
    private val service: AgentRunService
) : AgentLoop {
    private val logger = LoggerFactory.getLogger(DefaultAgentLoop::class.java)
    private val stacktrace = config.logging.stacktrace

    override fun run(request: AgentRunRequest): Flow<AgentEvent> = service.run(request).catch { e ->
        LogWrapper.error(logger, "[agent] loop error", e, stacktrace)
        throw e
    }
}
