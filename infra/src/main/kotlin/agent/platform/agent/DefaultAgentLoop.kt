package agent.platform.agent

import agent.platform.agent.domain.AgentLoop
import agent.platform.agent.domain.DomainEventPublisherPort
import agent.platform.agent.domain.TurnEvent
import agent.platform.config.PlatformConfig
import agent.platform.llm.ModelRefResolver
import agent.platform.llm.ProviderRegistry
import agent.platform.logging.LogWrapper
import agent.platform.session.Message
import agent.platform.session.MessageRole
import agent.platform.session.SessionManager
import agent.platform.session.SessionRepository
import agent.platform.tool.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import org.slf4j.LoggerFactory

/**
 * Infrastructure adapter that delegates to the AgentLoop domain aggregate.
 * 
 * This adapter wires infrastructure concerns (provider resolution, session management)
 * to the domain aggregate while keeping business logic in the domain layer.
 */
class DefaultAgentLoop(
    private val config: PlatformConfig,
    private val sessionManager: SessionManager,
    private val sessionRepository: SessionRepository,
    private val contextAssembler: ContextAssembler,
    private val toolRegistry: ToolRegistry,
    private val providerRegistry: ProviderRegistry,
    private val modelRefResolver: ModelRefResolver,
    private val eventPublisher: DomainEventPublisherPort? = null
) : agent.platform.agent.AgentLoop {
    private val logger = LoggerFactory.getLogger(DefaultAgentLoop::class.java)
    private val stacktrace = config.logging.stacktrace

    override fun run(request: AgentRunRequest): Flow<AgentEvent> = flow {
        val runId = request.runId
        
        sessionManager.withSessionLock(request.sessionKey) { session ->
            // Create the domain aggregate
            val agentLoop = AgentLoop.create(request.agentId)
            
            // Build user message
            val userMessage = Message(MessageRole.USER, request.inbound.text)

            // Build context bundle with model info
            val (sessionWithMessage, _) = session.withMessage(userMessage)
            val context = contextAssembler.build(sessionWithMessage, toolRegistry)
            val modelRef = resolveModel(request.agentId)
            val llmProvider = providerRegistry.get(modelRef.providerId)
            
            // Create context bundle with model/provider info
            val contextWithModelInfo = context.copy(
                systemPrompt = context.systemPrompt + "\n\nModel: ${modelRef.modelId}"
            )
            
            // Prepare dependencies for the domain aggregate
            val deps = AgentLoop.TurnDependencies(
                sessionRepository = sessionRepository,
                toolRegistry = toolRegistry,
                llmProvider = llmProvider,
                contextBundle = contextWithModelInfo,
                eventPublisher = eventPublisher
            )
            
            // Delegate to domain aggregate
            agentLoop.runTurn(runId, session, userMessage, deps).collect { turnEvent ->
                // Map domain turn events to infrastructure AgentEvents
                when (turnEvent) {
                    is TurnEvent.AssistantDelta -> {
                        emit(AgentEvent.AssistantDelta(
                            runId = turnEvent.runId,
                            text = turnEvent.text,
                            reasoning = turnEvent.reasoning,
                            done = turnEvent.done
                        ))
                    }
                    is TurnEvent.ToolStarted -> {
                        emit(AgentEvent.ToolEvent(
                            runId = turnEvent.runId,
                            tool = turnEvent.toolName,
                            phase = ToolPhase.START
                        ))
                    }
                    is TurnEvent.ToolCompleted -> {
                        emit(AgentEvent.ToolEvent(
                            runId = turnEvent.runId,
                            tool = turnEvent.toolName,
                            phase = ToolPhase.END,
                            payload = turnEvent.result.content
                        ))
                    }
                    is TurnEvent.ToolValidationError -> {
                        // Log validation errors but don't emit as they don't affect flow
                        logger.warn(
                            "[agent] Tool validation error: runId={}, tool={}, error={}",
                            turnEvent.runId.value,
                            turnEvent.toolName,
                            turnEvent.error
                        )
                    }
                    is TurnEvent.TurnCompleted -> {
                        logger.debug(
                            "[agent] Turn completed: runId={}, durationMs={}",
                            turnEvent.runId.value,
                            turnEvent.durationMs
                        )
                    }
                }
            }
            
            // Trigger compaction check
            sessionManager.maybeCompact(sessionWithMessage)
        }
    }.catch { e ->
        LogWrapper.error(logger, "[agent] loop error", e, stacktrace)
        throw e
    }

    private fun resolveModel(agentId: String): agent.platform.llm.ModelRef {
        val defaultModel = config.agents.defaults.model.primary
        val agent = config.agents.list.firstOrNull { it.id == agentId }
        val modelRef = agent?.model?.primary ?: defaultModel
        return modelRefResolver.resolve(modelRef)
    }
}
