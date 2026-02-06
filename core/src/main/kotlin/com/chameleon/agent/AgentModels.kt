package com.chameleon.agent

import com.chameleon.session.domain.SessionKey
import com.chameleon.sdk.InboundMessage
import java.time.Instant
import java.time.Duration

@JvmInline
value class RunId(val value: String)

data class AgentRunRequest(
    val runId: RunId = RunId("pending"),
    val sessionKey: SessionKey,
    val inbound: InboundMessage,
    val agentId: String,
    val receivedAt: Instant = Instant.now()
)

data class AgentRunHandle(
    val runId: RunId,
    val acceptedAt: Instant,
    val events: kotlinx.coroutines.flow.Flow<AgentEvent>
)

data class AgentRunResult(
    val runId: RunId,
    val status: RunStatus,
    val startedAt: Instant,
    val endedAt: Instant,
    val error: String? = null
)

enum class RunStatus {
    OK,
    ERROR,
    TIMEOUT
}

data class AgentWaitRequest(
    val runId: RunId,
    val timeout: Duration = Duration.ofSeconds(30)
)
