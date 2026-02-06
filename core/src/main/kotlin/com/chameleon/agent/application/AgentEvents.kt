package com.chameleon.agent.application

import com.chameleon.agent.domain.RunId

sealed interface AgentEvent {
    data class Lifecycle(
        val runId: RunId,
        val phase: Phase,
        val error: String? = null
    ) : AgentEvent

    data class AssistantDelta(
        val runId: RunId,
        val text: String,
        val reasoning: String? = null,
        val done: Boolean = false
    ) : AgentEvent

    data class ToolEvent(
        val runId: RunId,
        val tool: String,
        val phase: ToolPhase,
        val payload: String? = null
    ) : AgentEvent
}

enum class Phase {
    START,
    END,
    ERROR
}

enum class ToolPhase {
    START,
    UPDATE,
    END
}
