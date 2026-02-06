package com.chameleon.infrastructure.agent

import com.chameleon.agent.AgentEvent
import com.chameleon.agent.AgentLoop
import com.chameleon.agent.AgentRunHandle
import com.chameleon.agent.AgentRunRequest
import com.chameleon.agent.AgentRunResult
import com.chameleon.agent.AgentRuntime
import com.chameleon.agent.AgentWaitRequest
import com.chameleon.agent.Phase
import com.chameleon.agent.RunId
import com.chameleon.agent.RunStatus
import com.chameleon.infrastructure.logging.LogWrapper
import com.chameleon.config.domain.PlatformConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of [AgentRuntime] managing concurrent agent runs.
 *
 * Handles:
 * - Run lifecycle management (start, wait, complete)
 * - Event streaming via Kotlin Flow
 * - Timeout handling for synchronous waits
 * - Error handling and status tracking
 */
class DefaultAgentRuntime(
    private val config: PlatformConfig,
    private val loop: AgentLoop
) : AgentRuntime {
    private val logger = LoggerFactory.getLogger(DefaultAgentRuntime::class.java)
    private val stacktrace = config.logging.stacktrace
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val runs = ConcurrentHashMap<RunId, RunState>()

    /**
     * Starts a new agent run asynchronously.
     *
     * @param request The run request containing session and execution parameters
     * @return Handle to the started run with event flow
     */
    override fun start(request: AgentRunRequest): AgentRunHandle {
        val runId = RunId(UUID.randomUUID().toString())
        val acceptedAt = Instant.now()
        val events = Channel<AgentEvent>(Channel.BUFFERED)
        runs[runId] = RunState(runId, acceptedAt, events)

        scope.launch {
            val startedAt = Instant.now()
            runs[runId]?.startedAt = startedAt
            try {
                events.send(AgentEvent.Lifecycle(runId, Phase.START))
                loop.run(request.copy(runId = runId)).collect { events.send(it) }
                runs[runId]?.finish(RunStatus.OK, startedAt, Instant.now())
                events.send(AgentEvent.Lifecycle(runId, Phase.END))
            } catch (e: Exception) {
                LogWrapper.error(logger, "[agent] run failed", e, stacktrace)
                runs[runId]?.finish(RunStatus.ERROR, startedAt, Instant.now(), e.message)
                events.send(AgentEvent.Lifecycle(runId, Phase.ERROR, e.message))
            } finally {
                events.close()
                runs.remove(runId)
            }
        }

        return AgentRunHandle(runId, acceptedAt, events.receiveAsFlow())
    }

    /**
     * Waits for a run to complete with optional timeout.
     *
     * @param request Wait request containing run ID and timeout
     * @return Result of the run (success, error, or timeout)
     */
    override suspend fun wait(request: AgentWaitRequest): AgentRunResult {
        val state = runs[request.runId] ?: return AgentRunResult(
            runId = request.runId,
            status = RunStatus.ERROR,
            startedAt = Instant.now(),
            endedAt = Instant.now(),
            error = "unknown runId"
        )

        val timeout = request.timeout
        return withTimeoutOrNull(timeout.toMillis()) {
            state.await()
        } ?: AgentRunResult(
            runId = request.runId,
            status = RunStatus.TIMEOUT,
            startedAt = state.startedAt ?: Instant.now(),
            endedAt = Instant.now(),
            error = "timeout"
        )
    }

    private data class RunState(
        val runId: RunId,
        val acceptedAt: Instant,
        val events: Channel<AgentEvent>
    ) {
        @Volatile
        var startedAt: Instant? = null
        @Volatile
        var result: AgentRunResult? = null

        suspend fun await(): AgentRunResult {
            while (result == null) {
                delay(10)
            }
            return result ?: AgentRunResult(
                runId = runId,
                status = RunStatus.ERROR,
                startedAt = Instant.now(),
                endedAt = Instant.now(),
                error = "missing result"
            )
        }

        fun finish(status: RunStatus, startedAt: Instant, endedAt: Instant, error: String? = null) {
            this.startedAt = startedAt
            this.result = AgentRunResult(
                runId = runId,
                status = status,
                startedAt = startedAt,
                endedAt = endedAt,
                error = error
            )
        }
    }
}
