package com.chameleon.agent.domain

import com.chameleon.agent.RunId
import com.chameleon.tool.domain.ToolCallRequest
import com.chameleon.tool.port.ToolDefinitionRegistry
import com.chameleon.llm.ChatCompletionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AgentLoop aggregate - Domain logic for a single turn.
 *
 * This aggregate does not call external services or persist state.
 * It validates tool calls, aggregates assistant output, and emits TurnEvents.
 */
class AgentLoop private constructor(
    val agentId: String
) {
    companion object {
        fun create(agentId: String): AgentLoop {
            return AgentLoop(agentId)
        }
    }

    data class TurnDependencies(
        val toolRegistry: ToolDefinitionRegistry
    )

    data class TurnPlan(
        val runId: RunId,
        val assistantText: String,
        val toolCalls: List<ToolCallRequest>,
        val completionTokens: Int
    )

    fun processCompletion(
        runId: RunId,
        events: Flow<ChatCompletionEvent>,
        deps: TurnDependencies
    ): Flow<TurnEvent> = flow {
        val toolCalls = mutableListOf<ToolCallRequest>()
        val assistantBuffer = StringBuilder()
        var completionTokens = 0

        events.collect { event ->
            when (event) {
                is ChatCompletionEvent.AssistantDelta -> {
                    completionTokens += estimateTokens(event.text)
                    emit(TurnEvent.AssistantDelta(runId, event.text, event.reasoning))
                    if (event.text.isNotBlank()) {
                        assistantBuffer.append(event.text)
                    }
                }
                is ChatCompletionEvent.ToolCallDelta -> {
                    val toolDef = deps.toolRegistry.get(event.name)
                    if (toolDef != null) {
                        toolCalls.add(ToolCallRequest(event.id, event.name, event.argumentsJson))
                    } else {
                        emit(TurnEvent.ToolValidationError(runId, event.name, "Tool not found in registry"))
                    }
                }
                is ChatCompletionEvent.Completed -> {
                    emit(TurnEvent.AssistantDelta(runId, "", null, done = true))
                }
            }
        }

        emit(
            TurnEvent.TurnCompleted(
                runId = runId,
                durationMs = 0,
                plan = TurnPlan(
                    runId = runId,
                    assistantText = assistantBuffer.toString().trim(),
                    toolCalls = toolCalls,
                    completionTokens = completionTokens
                )
            )
        )
    }

    private fun estimateTokens(text: String): Int {
        return text.length / 4
    }
}

sealed interface TurnEvent {
    val runId: RunId

    data class AssistantDelta(
        override val runId: RunId,
        val text: String,
        val reasoning: String? = null,
        val done: Boolean = false
    ) : TurnEvent

    data class ToolValidationError(
        override val runId: RunId,
        val toolName: String,
        val error: String
    ) : TurnEvent

    data class TurnCompleted(
        override val runId: RunId,
        val durationMs: Long,
        val plan: AgentLoop.TurnPlan
    ) : TurnEvent
}
