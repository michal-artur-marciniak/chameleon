package agent.platform.agent

import agent.platform.config.PlatformConfig
import agent.platform.llm.ChatCompletionEvent
import agent.platform.llm.ChatCompletionRequest
import agent.platform.llm.LlmProviderPort
import agent.platform.logging.LogWrapper
import agent.platform.session.Message
import agent.platform.session.MessageRole
import agent.platform.session.SessionManager
import agent.platform.tool.ToolCallRequest
import agent.platform.tool.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import org.slf4j.LoggerFactory

class DefaultAgentLoop(
    private val config: PlatformConfig,
    private val sessionManager: SessionManager,
    private val contextAssembler: ContextAssembler,
    private val toolRegistry: ToolRegistry,
    private val llmProvider: LlmProviderPort
) : AgentLoop {
    private val logger = LoggerFactory.getLogger(DefaultAgentLoop::class.java)
    private val stacktrace = config.logging.stacktrace

    override fun run(request: AgentRunRequest): Flow<AgentEvent> = flow {
        val runId = request.runId
        sessionManager.withSessionLock(request.sessionKey) { session ->
            val userMessage = Message(MessageRole.USER, request.inbound.text)
            val updatedSession = session.withMessage(userMessage)
            sessionManager.append(updatedSession.id, userMessage)

            val context = contextAssembler.build(updatedSession, toolRegistry)
            val llmRequest = ChatCompletionRequest(
                model = resolveModel(request.agentId),
                messages = toChatMessages(context),
                toolSchemasJson = context.toolSchemasJson
            )

            val toolCalls = mutableListOf<ToolCallRequest>()
            val assistantBuffer = StringBuilder()
            llmProvider.stream(llmRequest).collect { event ->
                when (event) {
                    is ChatCompletionEvent.AssistantDelta -> {
                        emit(AgentEvent.AssistantDelta(runId, event.text, event.reasoning))
                        if (!event.text.isBlank()) {
                            assistantBuffer.append(event.text)
                        }
                    }
                    is ChatCompletionEvent.ToolCallDelta -> {
                        toolCalls.add(
                            ToolCallRequest(event.id, event.name, event.argumentsJson)
                        )
                    }
                    is ChatCompletionEvent.Completed -> {
                        emit(AgentEvent.AssistantDelta(runId, "", done = true))
                    }
                }
            }

            val assistantText = assistantBuffer.toString().trim()
            if (assistantText.isNotBlank()) {
                sessionManager.append(
                    updatedSession.id,
                    Message(role = MessageRole.ASSISTANT, content = assistantText)
                )
            }

            toolCalls.forEach { call ->
                emit(AgentEvent.ToolEvent(runId, call.name, ToolPhase.START))
                val result = toolRegistry.execute(call)
                emit(AgentEvent.ToolEvent(runId, call.name, ToolPhase.END, result.content))
                sessionManager.append(
                    updatedSession.id,
                    Message(
                        role = MessageRole.TOOL,
                        content = result.content,
                        toolCallId = call.id
                    )
                )
            }

            sessionManager.maybeCompact(updatedSession)
        }
    }.catch { e ->
        LogWrapper.error(logger, "[agent] loop error", e, stacktrace)
        throw e
    }

    private fun resolveModel(agentId: String): String {
        val defaultModel = config.agents.defaults.model.primary
        val agent = config.agents.list.firstOrNull { it.id == agentId }
        return agent?.model?.primary ?: defaultModel
    }

    private fun toChatMessages(context: ContextBundle): List<agent.platform.llm.ChatMessage> {
        val messages = mutableListOf(
            agent.platform.llm.ChatMessage("system", context.systemPrompt)
        )
        context.messages.forEach { message ->
            messages.add(agent.platform.llm.ChatMessage(toRole(message.role), message.content))
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
}
