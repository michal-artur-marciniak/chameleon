package agent.platform.agent.domain

import agent.platform.agent.ContextBundle
import agent.platform.agent.RunId
import agent.platform.llm.ChatCompletionEvent
import agent.platform.llm.ChatCompletionRequest
import agent.platform.llm.ChatMessage
import agent.platform.llm.LlmProviderPort
import agent.platform.session.Message
import agent.platform.session.MessageRole
import agent.platform.session.Session
import agent.platform.session.SessionId
import agent.platform.session.SessionRepository
import agent.platform.tool.ToolCallRequest
import agent.platform.tool.ToolRegistry
import agent.platform.tool.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID

/**
 * AgentLoop aggregate - Core domain orchestration for agent turns.
 * 
 * This aggregate owns the runTurn() method and enforces:
 * - Tool call validation via ToolRegistry
 * - Assistant response persistence in the session
 * - Atomic turn execution per session
 * 
 * Factory methods:
 * - create(agentId, config) - Factory method for creating a new AgentLoop instance
 * - runTurn(session, inboundMessage, dependencies) - Execute a single turn
 */
class AgentLoop private constructor(
    val agentId: String
) {
    companion object {
        /**
         * Factory method: Creates a new AgentLoop aggregate for the given agent.
         */
        fun create(agentId: String): AgentLoop {
            return AgentLoop(agentId)
        }
    }

    /**
     * Dependencies required for running a turn.
     * These are passed in rather than injected to keep the aggregate pure.
     */
    data class TurnDependencies(
        val sessionRepository: SessionRepository,
        val toolRegistry: ToolRegistry,
        val llmProvider: LlmProviderPort,
        val contextBundle: ContextBundle,
        val eventPublisher: DomainEventPublisherPort? = null
    )

    /**
     * Executes a single turn for this agent.
     * 
     * @param runId The unique run identifier
     * @param session The current session
     * @param userMessage The user's inbound message
     * @param deps The turn dependencies
     * @return Flow of turn events (assistant deltas, tool events)
     */
    fun runTurn(
        runId: RunId,
        session: Session,
        userMessage: Message,
        deps: TurnDependencies
    ): Flow<TurnEvent> = flow {
        val startTime = Instant.now()
        
        // 1. Add user message to session
        val (sessionWithUserMessage, messageAddedEvent) = session.withMessage(userMessage)
        deps.sessionRepository.appendMessage(session.id, userMessage)

        deps.eventPublisher?.publish(
            AgentLoopDomainEvent.MessageAdded(
                runId = runId,
                sessionId = session.id,
                role = "user",
                messageId = messageAddedEvent.messageId
            )
        )

        // 2. Build LLM request
        val llmRequest = ChatCompletionRequest(
            model = "default",
            messages = buildChatMessages(deps.contextBundle, sessionWithUserMessage),
            toolSchemasJson = deps.contextBundle.toolSchemasJson
        )

        deps.eventPublisher?.publish(
            AgentLoopDomainEvent.LlmCompletionRequested(
                runId = runId,
                providerId = "default",
                modelId = "default"
            )
        )

        // 3. Stream LLM completion
        val toolCalls = mutableListOf<ToolCallRequest>()
        val assistantBuffer = StringBuilder()
        var completionTokens = 0

        deps.llmProvider.stream(llmRequest).collect { event ->
            when (event) {
                is ChatCompletionEvent.AssistantDelta -> {
                    completionTokens += estimateTokens(event.text)
                    emit(TurnEvent.AssistantDelta(runId, event.text, event.reasoning))
                    if (event.text.isNotBlank()) {
                        assistantBuffer.append(event.text)
                    }
                }
                is ChatCompletionEvent.ToolCallDelta -> {
                    // Validate tool exists before accepting
                    val toolDef = deps.toolRegistry.get(event.name)
                    if (toolDef != null) {
                        toolCalls.add(ToolCallRequest(event.id, event.name, event.argumentsJson))
                    } else {
                        // Emit error for unknown tool, but continue
                        emit(TurnEvent.ToolValidationError(runId, event.name, "Tool not found in registry"))
                    }
                }
                is ChatCompletionEvent.Completed -> {
                    emit(TurnEvent.AssistantDelta(runId, "", null, done = true))
                }
            }
        }

        deps.eventPublisher?.publish(
            AgentLoopDomainEvent.LlmCompletionReceived(
                runId = runId,
                providerId = "default",
                modelId = "default",
                completionTokens = completionTokens,
                promptTokens = estimateTokens(deps.contextBundle.systemPrompt)
            )
        )

        // 4. Persist assistant response
        val assistantText = assistantBuffer.toString().trim()
        if (assistantText.isNotBlank()) {
            val assistantMessage = Message(
                role = MessageRole.ASSISTANT,
                content = assistantText
            )
            deps.sessionRepository.appendMessage(session.id, assistantMessage)
            
            deps.eventPublisher?.publish(
                AgentLoopDomainEvent.ResponseGenerated(
                    runId = runId,
                    messageId = UUID.randomUUID().toString(),
                    tokenCount = completionTokens
                )
            )
            
            deps.eventPublisher?.publish(
                AgentLoopDomainEvent.MessageAdded(
                    runId = runId,
                    sessionId = session.id,
                    role = "assistant",
                    messageId = UUID.randomUUID().toString()
                )
            )
        }

        // 5. Execute validated tool calls
        toolCalls.forEach { call ->
            deps.eventPublisher?.publish(
                AgentLoopDomainEvent.ToolCallInitiated(
                    runId = runId,
                    toolName = call.name,
                    toolCallId = call.id,
                    argumentsJson = call.argumentsJson
                )
            )

            emit(TurnEvent.ToolStarted(runId, call.name, call.id))
            
            val toolStartTime = Instant.now()
            val result = executeTool(call, deps.toolRegistry)
            val durationMs = Instant.now().toEpochMilli() - toolStartTime.toEpochMilli()
            
            emit(TurnEvent.ToolCompleted(runId, call.name, call.id, result))
            
            // Persist tool result
            deps.sessionRepository.appendMessage(
                session.id,
                Message(
                    role = MessageRole.TOOL,
                    content = result.content,
                    toolCallId = call.id
                )
            )

            deps.eventPublisher?.publish(
                AgentLoopDomainEvent.ToolExecuted(
                    runId = runId,
                    toolName = call.name,
                    toolCallId = call.id,
                    success = !result.isError,
                    durationMs = durationMs,
                    resultSummary = result.content.take(200) // Truncate for event
                )
            )
        }

        // 6. Emit completion
        val totalDurationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli()
        emit(TurnEvent.TurnCompleted(runId, totalDurationMs))
    }

    /**
     * Executes a tool call with validation and policy enforcement.
     * Tool validation happens before execution via the ToolRegistry.
     */
    private suspend fun executeTool(
        call: ToolCallRequest,
        toolRegistry: ToolRegistry
    ): ToolResult {
        // Validate: Tool must exist in registry
        if (!toolRegistry.isRegistered(call.name)) {
            return ToolResult(
                content = "Error: Tool '${call.name}' not found in registry",
                isError = true
            )
        }

        // Validate: Check policy before execution (allows early detection)
        val policyDecision = toolRegistry.validatePolicy(call)
        when (policyDecision) {
            is agent.platform.tool.ToolPolicyService.PolicyDecision.Deny -> {
                return ToolResult(
                    content = "Error: ${policyDecision.reason}",
                    isError = true
                )
            }
            is agent.platform.tool.ToolPolicyService.PolicyDecision.Ask -> {
                return ToolResult(
                    content = "Approval required: ${policyDecision.reason}",
                    isError = true
                )
            }
            is agent.platform.tool.ToolPolicyService.PolicyDecision.Allow -> {
                // Continue to execution
            }
        }

        // Execute via registry (registry handles the actual execution with policy enforcement)
        return try {
            toolRegistry.execute(call)
        } catch (e: Exception) {
            ToolResult(
                content = "Error executing tool '${call.name}': ${e.message}",
                isError = true
            )
        }
    }

    private fun buildChatMessages(
        context: ContextBundle,
        session: Session
    ): List<ChatMessage> {
        val messages = mutableListOf(
            ChatMessage("system", context.systemPrompt)
        )
        session.messages.forEach { message ->
            messages.add(ChatMessage(toRole(message.role), message.content))
        }
        return messages
    }

    private fun toRole(role: MessageRole): String {
        return when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.TOOL -> "tool"
        }
    }

    private fun estimateTokens(text: String): Int {
        // Rough estimation: ~4 characters per token
        return text.length / 4
    }
}

/**
 * Events emitted during a turn execution.
 * These are separate from domain events - they are for the caller to consume.
 */
sealed interface TurnEvent {
    val runId: RunId

    data class AssistantDelta(
        override val runId: RunId,
        val text: String,
        val reasoning: String? = null,
        val done: Boolean = false
    ) : TurnEvent

    data class ToolStarted(
        override val runId: RunId,
        val toolName: String,
        val toolCallId: String
    ) : TurnEvent

    data class ToolCompleted(
        override val runId: RunId,
        val toolName: String,
        val toolCallId: String,
        val result: ToolResult
    ) : TurnEvent

    data class ToolValidationError(
        override val runId: RunId,
        val toolName: String,
        val error: String
    ) : TurnEvent

    data class TurnCompleted(
        override val runId: RunId,
        val durationMs: Long
    ) : TurnEvent
}

/**
 * Extended ContextBundle that includes model/provider info for the domain.
 */
val ContextBundle.providerId: String?
    get() = this.report.injectedFiles.firstOrNull()?.path?.let { "default" }

val ContextBundle.modelId: String?
    get() = "default"
