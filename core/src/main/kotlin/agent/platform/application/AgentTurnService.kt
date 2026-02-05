package agent.platform.application

import agent.platform.agent.AgentEvent
import agent.platform.agent.AgentLoop
import agent.platform.agent.AgentRunRequest
import agent.platform.agent.ContextAssembler
import agent.platform.agent.RunId
import agent.platform.agent.ContextBundle
import agent.platform.agent.ToolPhase
import agent.platform.agent.domain.AgentLoop as AgentLoopAggregate
import agent.platform.agent.domain.AgentLoopDomainEvent
import agent.platform.agent.ports.DomainEventPublisherPort
import agent.platform.agent.domain.TurnEvent
import agent.platform.application.llm.LlmRequestBuilder
import agent.platform.application.memory.MemoryContextAssembler
import agent.platform.config.PlatformConfig
import agent.platform.llm.ModelRefResolutionError
import agent.platform.llm.ModelRefResolver
import agent.platform.llm.ModelRef
import agent.platform.llm.ports.LlmProviderPort
import agent.platform.llm.ports.LlmProviderRepositoryPort
import agent.platform.session.Message
import agent.platform.session.MessageRole
import agent.platform.session.Session
import agent.platform.session.SessionManager
import agent.platform.session.SessionRepository
import agent.platform.tool.ToolCallRequest
import agent.platform.tool.ToolDefinitionRegistry
import agent.platform.application.ToolExecutionService
import agent.platform.application.SessionAppService
import agent.platform.tool.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID

class AgentTurnService(
    private val config: PlatformConfig,
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
) : AgentLoop {
    override fun run(request: AgentRunRequest): Flow<AgentEvent> = flow {
        val runId = request.runId

        sessionManager.withSessionLock(request.sessionKey) { session ->
            val contextState = buildTurnContext(session, request)
            val deps = AgentLoopAggregate.TurnDependencies(toolRegistry = toolDefinitionRegistry)

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
            var turnPlan: AgentLoopAggregate.TurnPlan? = null

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

    private fun resolveModel(agentId: String): ModelRef {
        val defaultModel = config.agents.defaults.model.primary
        val agent = config.agents.list.firstOrNull { it.id == agentId }
        val modelRef = agent?.model?.primary ?: defaultModel
        val resolution = modelRefResolver.resolve(modelRef)
        return resolution.modelRef ?: throw IllegalStateException(formatModelRefError(modelRef, resolution.error))
    }

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
        val agentLoop: AgentLoopAggregate,
        val session: Session,
        val sessionWithMessage: Session,
        val context: ContextBundle,
        val modelRef: ModelRef,
        val llmProvider: LlmProviderPort
    )

    private fun buildTurnContext(
        session: Session,
        request: AgentRunRequest
    ): TurnContext {
        val agentLoop = AgentLoopAggregate.create(request.agentId)
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

    private fun persistAssistantResponse(
        contextState: TurnContext,
        runId: RunId,
        plan: AgentLoopAggregate.TurnPlan
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


    private suspend fun executeTool(call: ToolCallRequest): ToolResult {
        return toolExecutionService.execute(call)
    }
}
