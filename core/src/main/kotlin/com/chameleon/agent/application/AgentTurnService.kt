package com.chameleon.agent.application

import com.chameleon.agent.port.AgentLoopPort
import com.chameleon.agent.domain.RunId
import com.chameleon.agent.domain.AgentLoop
import com.chameleon.agent.domain.AgentLoopDomainEvent
import com.chameleon.agent.port.DomainEventPublisherPort
import com.chameleon.agent.domain.TurnEvent
import com.chameleon.agent.domain.AgentsConfig
import com.chameleon.llm.application.LlmRequestBuilder
import com.chameleon.llm.domain.ModelRef
import com.chameleon.llm.domain.ModelRefResolutionError
import com.chameleon.llm.domain.ModelRefResolver
import com.chameleon.llm.port.LlmProviderPort
import com.chameleon.llm.port.LlmProviderRepositoryPort
import com.chameleon.memory.application.MemoryContextAssembler
import com.chameleon.session.domain.Message
import com.chameleon.session.domain.MessageRole
import com.chameleon.session.domain.Session
import com.chameleon.session.application.SessionAppService
import com.chameleon.session.port.SessionManager
import com.chameleon.session.port.SessionRepository
import com.chameleon.tool.domain.ToolCallRequest
import com.chameleon.tool.domain.ToolResult
import com.chameleon.tool.application.ToolExecutionService
import com.chameleon.tool.port.ToolDefinitionRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID

/**
 * Core service that orchestrates a single agent turn.
 *
 * Handles the complete turn lifecycle:
 * 1. Build turn context (session, tools, model resolution)
 * 2. Stream LLM completion with tool calls
 * 3. Execute tool calls with policy validation
 * 4. Persist messages and publish domain events
 * 5. Trigger session compaction if needed
 *
 * @property agentsConfig Agent configuration for model defaults
 * @property sessionManager Manages session locking
 * @property sessionRepository Persists session messages
 * @property sessionAppService Handles session compaction
 * @property contextAssembler Builds context bundles with system prompts and tools
 * @property toolDefinitionRegistry Registry of available tools
 * @property toolExecutionService Executes tool calls with policy validation
 * @property providerRegistry Registry of LLM providers
 * @property modelRefResolver Resolves model references to provider/model pairs
 * @property eventPublisher Optional publisher for domain events (observability)
 * @property llmRequestBuilder Builds LLM completion requests
 * @property memoryContextAssembler Optional assembler for injecting memory context
 */
class AgentTurnService(
    private val agentsConfig: AgentsConfig,
    private val sessionManager: SessionManager,
    private val sessionRepository: SessionRepository,
    private val sessionAppService: SessionAppService,
    private val contextAssembler: ContextAssembler,
    private val toolDefinitionRegistry: ToolDefinitionRegistry,
    private val toolExecutionService: ToolExecutionService,
    private val providerRegistry: LlmProviderRepositoryPort,
    private val modelRefResolver: ModelRefResolver,
    private val eventPublisher: DomainEventPublisherPort? = null,
    private val llmRequestBuilder: LlmRequestBuilder = LlmRequestBuilder(),
    private val memoryContextAssembler: MemoryContextAssembler? = null
) : AgentLoopPort {
    /**
     * Executes a single agent turn.
     *
     * The turn includes: building context, calling LLM, executing any tool calls,
     * persisting messages, publishing events, and optionally compacting the session.
     *
     * @param request The run request containing session key, agent ID, and inbound message
     * @return Flow of agent events streamed during the turn:
     *         - [AgentEvent.AssistantDelta] for streaming assistant responses
     *         - [AgentEvent.ToolEvent] for tool execution lifecycle
     *         - [AgentEvent.Lifecycle] for run start/end/error
     */
    override fun run(request: AgentRunRequest): Flow<AgentEvent> = flow {
        val runId = request.runId

        sessionManager.withSessionLock(request.sessionKey) { session ->
            val contextState = buildTurnContext(session, request)
            val deps = AgentLoop.TurnDependencies(toolRegistry = toolDefinitionRegistry)

            val requestPlan = llmRequestBuilder.build(
                context = contextState.context,
                modelId = contextState.modelRef.modelId,
                memoryContext = memoryContextAssembler?.buildContext(contextState.context.messages)
            )
            eventPublisher?.publish(
                AgentLoopDomainEvent.LlmCompletionRequested(
                    runId = runId,
                    providerId = contextState.modelRef.providerId,
                    modelId = contextState.modelRef.modelId
                )
            )

            val startTime = Instant.now()
            var turnPlan: AgentLoop.TurnPlan? = null

            contextState.agentLoop
                .processCompletion(runId, contextState.llmProvider.stream(requestPlan.request), deps)
                .collect { turnEvent ->
                    when (turnEvent) {
                        is TurnEvent.AssistantDelta -> emit(
                            AgentEvent.AssistantDelta(
                                runId = turnEvent.runId,
                                text = turnEvent.text,
                                reasoning = turnEvent.reasoning,
                                done = turnEvent.done
                            )
                        )
                        is TurnEvent.ToolValidationError -> Unit
                        is TurnEvent.TurnCompleted -> {
                            turnPlan = turnEvent.plan
                        }
                    }
                }

            val plan = turnPlan
            if (plan != null) {
                eventPublisher?.publish(
                    AgentLoopDomainEvent.LlmCompletionReceived(
                        runId = runId,
                        providerId = contextState.modelRef.providerId,
                        modelId = contextState.modelRef.modelId,
                        completionTokens = plan.completionTokens,
                        promptTokens = requestPlan.promptTokens
                    )
                )

                persistAssistantResponse(contextState, runId, plan)
                executeToolCalls(contextState, runId, plan.toolCalls) { event ->
                    emit(event)
                }

                val totalDurationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli()
                eventPublisher?.publish(
                    AgentLoopDomainEvent.ToolExecuted(
                        runId = runId,
                        toolName = "turn",
                        toolCallId = "",
                        success = true,
                        durationMs = totalDurationMs,
                        resultSummary = "Turn completed"
                    )
                )
            }

            sessionAppService.maybeCompact(contextState.sessionWithMessage)

            eventPublisher?.publish(
                AgentLoopDomainEvent.AgentLoopCompleted(
                    runId = runId,
                    sessionId = contextState.session.id,
                    success = true,
                    error = null
                )
            )
        }
    }

    /**
     * Resolves the model reference for the given agent.
     *
     * Uses agent-specific model config if available, otherwise falls back to default.
     *
     * @param agentId The agent identifier
     * @return Resolved model reference with provider ID and model ID
     * @throws IllegalStateException if the model reference cannot be resolved
     */
    private fun resolveModel(agentId: String): ModelRef {
        val defaultModel = agentsConfig.defaults.model.primary
        val agent = agentsConfig.list.firstOrNull { it.id == agentId }
        val modelRef = agent?.model?.primary ?: defaultModel
        val resolution = modelRefResolver.resolve(modelRef)
        return resolution.modelRef ?: throw IllegalStateException(formatModelRefError(modelRef, resolution.error))
    }

    /**
     * Formats model reference resolution errors into human-readable messages.
     *
     * @param modelRef The model reference that failed to resolve
     * @param error The specific resolution error
     * @return Formatted error message
     */
    private fun formatModelRefError(modelRef: String, error: ModelRefResolutionError?): String {
        return when (error) {
            is ModelRefResolutionError.BlankModelRef -> error.message
            is ModelRefResolutionError.MissingModelId -> error.message
            is ModelRefResolutionError.NoProvidersConfigured -> error.message
            is ModelRefResolutionError.UnknownProvider ->
                "Unknown provider '${error.providerId}'. Configured: ${error.configured.sorted()}"
            is ModelRefResolutionError.ProviderPrefixRequired -> error.message
            null -> "Failed to resolve model ref '$modelRef'"
        }
    }

    private data class TurnContext(
        val agentLoop: AgentLoop,
        val session: Session,
        val sessionWithMessage: Session,
        val context: ContextBundle,
        val modelRef: ModelRef,
        val llmProvider: LlmProviderPort
    )

    /**
     * Builds the turn context including agent loop, messages, tools, and model info.
     *
     * Persists the user message and publishes start events.
     *
     * @param session The current session
     * @param request The run request
     * @return Complete turn context ready for LLM completion
     */
    private fun buildTurnContext(
        session: Session,
        request: AgentRunRequest
    ): TurnContext {
        val agentLoop = AgentLoop.create(request.agentId)
        val userMessage = Message(MessageRole.USER, request.inbound.text)
        val (sessionWithMessage, messageAddedEvent) = session.withMessage(userMessage)
        val context = contextAssembler.build(sessionWithMessage, toolDefinitionRegistry)
        val modelRef = resolveModel(request.agentId)
        val llmProvider = providerRegistry.get(modelRef.providerId)
            ?: throw IllegalStateException("LLM provider not registered: ${modelRef.providerId}")

        val contextWithModelInfo = context.copy(
            systemPrompt = context.systemPrompt + "\n\nModel: ${modelRef.modelId}"
        )

        eventPublisher?.publish(
            AgentLoopDomainEvent.AgentLoopStarted(
                runId = request.runId,
                sessionId = session.id,
                agentId = request.agentId,
                sessionKey = request.sessionKey
            )
        )

        sessionRepository.appendMessage(session.id, userMessage)
        eventPublisher?.publish(
            AgentLoopDomainEvent.MessageAdded(
                runId = request.runId,
                sessionId = session.id,
                role = "user",
                messageId = messageAddedEvent.messageId
            )
        )

        return TurnContext(
            agentLoop = agentLoop,
            session = session,
            sessionWithMessage = sessionWithMessage,
            context = contextWithModelInfo,
            modelRef = modelRef,
            llmProvider = llmProvider
        )
    }

    /**
     * Persists the assistant's response message if non-empty.
     *
     * @param contextState The turn context
     * @param runId The current run ID
     * @param plan The turn plan containing the assistant's text
     */
    private fun persistAssistantResponse(
        contextState: TurnContext,
        runId: RunId,
        plan: AgentLoop.TurnPlan
    ) {
        if (plan.assistantText.isBlank()) return

        val assistantMessage = Message(
            role = MessageRole.ASSISTANT,
            content = plan.assistantText
        )
        sessionRepository.appendMessage(contextState.session.id, assistantMessage)
        eventPublisher?.publish(
            AgentLoopDomainEvent.ResponseGenerated(
                runId = runId,
                messageId = UUID.randomUUID().toString(),
                tokenCount = plan.completionTokens
            )
        )
        eventPublisher?.publish(
            AgentLoopDomainEvent.MessageAdded(
                runId = runId,
                sessionId = contextState.session.id,
                role = "assistant",
                messageId = UUID.randomUUID().toString()
            )
        )
    }

    /**
     * Executes a list of tool calls sequentially.
     *
     * Publishes events for each tool call and persists tool results as messages.
     *
     * @param contextState The turn context
     * @param runId The current run ID
     * @param toolCalls The tool calls to execute
     * @param emitEvent Callback to emit tool events to the flow
     */
    private suspend fun executeToolCalls(
        contextState: TurnContext,
        runId: RunId,
        toolCalls: List<ToolCallRequest>,
        emitEvent: suspend (AgentEvent) -> Unit
    ) {
        for (call in toolCalls) {
            eventPublisher?.publish(
                AgentLoopDomainEvent.ToolCallInitiated(
                    runId = runId,
                    toolName = call.name,
                    toolCallId = call.id,
                    argumentsJson = call.argumentsJson
                )
            )

            emitEvent(AgentEvent.ToolEvent(runId, call.name, ToolPhase.START))

            val toolStartTime = Instant.now()
            val result = executeTool(call)
            val durationMs = Instant.now().toEpochMilli() - toolStartTime.toEpochMilli()

            emitEvent(AgentEvent.ToolEvent(runId, call.name, ToolPhase.END, result.content))

            sessionRepository.appendMessage(
                contextState.session.id,
                Message(
                    role = MessageRole.TOOL,
                    content = result.content,
                    toolCallId = call.id
                )
            )

            eventPublisher?.publish(
                AgentLoopDomainEvent.ToolExecuted(
                    runId = runId,
                    toolName = call.name,
                    toolCallId = call.id,
                    success = !result.isError,
                    durationMs = durationMs,
                    resultSummary = result.content.take(200)
                )
            )
        }
    }


    /**
     * Delegates tool execution to [toolExecutionService].
     *
     * @param call The tool call request to execute
     * @return The tool execution result
     */
    private suspend fun executeTool(call: ToolCallRequest): ToolResult {
        return toolExecutionService.execute(call)
    }
}
