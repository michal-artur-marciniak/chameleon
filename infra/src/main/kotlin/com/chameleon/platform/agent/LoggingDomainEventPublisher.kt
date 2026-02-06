package com.chameleon.agent

import com.chameleon.agent.domain.AgentLoopDomainEvent
import com.chameleon.agent.port.DomainEventPublisherPort
import org.slf4j.LoggerFactory

/**
 * Adapter that logs domain events.
 * This is the default implementation for observability.
 */
class LoggingDomainEventPublisher : DomainEventPublisherPort {
    private val logger = LoggerFactory.getLogger(LoggingDomainEventPublisher::class.java)
    
    override fun publish(event: AgentLoopDomainEvent) {
        when (event) {
            is AgentLoopDomainEvent.AgentLoopStarted -> {
                logger.info(
                    "[domain-event] AgentLoopStarted: runId={}, sessionId={}, agentId={}",
                    event.runId.value,
                    event.sessionId.value,
                    event.agentId
                )
            }
            is AgentLoopDomainEvent.ToolCallInitiated -> {
                logger.debug(
                    "[domain-event] ToolCallInitiated: runId={}, tool={}",
                    event.runId.value,
                    event.toolName
                )
            }
            is AgentLoopDomainEvent.ToolExecuted -> {
                logger.debug(
                    "[domain-event] ToolExecuted: runId={}, tool={}, success={}",
                    event.runId.value,
                    event.toolName,
                    event.success
                )
            }
            is AgentLoopDomainEvent.ResponseGenerated -> {
                logger.debug(
                    "[domain-event] ResponseGenerated: runId={}, messageId={}",
                    event.runId.value,
                    event.messageId
                )
            }
            is AgentLoopDomainEvent.MessageAdded -> {
                logger.debug(
                    "[domain-event] MessageAdded: runId={}, sessionId={}, role={}",
                    event.runId.value,
                    event.sessionId.value,
                    event.role
                )
            }
            is AgentLoopDomainEvent.LlmCompletionRequested -> {
                logger.debug(
                    "[domain-event] LlmCompletionRequested: runId={}, provider={}, model={}",
                    event.runId.value,
                    event.providerId,
                    event.modelId
                )
            }
            is AgentLoopDomainEvent.LlmCompletionReceived -> {
                logger.debug(
                    "[domain-event] LlmCompletionReceived: runId={}, provider={}, model={}",
                    event.runId.value,
                    event.providerId,
                    event.modelId
                )
            }
            is AgentLoopDomainEvent.LlmError -> {
                logger.error(
                    "[domain-event] LlmError: runId={}, provider={}, model={}, error={}",
                    event.runId.value,
                    event.providerId,
                    event.modelId,
                    event.error
                )
            }
            is AgentLoopDomainEvent.AgentLoopCompleted -> {
                logger.info(
                    "[domain-event] AgentLoopCompleted: runId={}, sessionId={}, success={}",
                    event.runId.value,
                    event.sessionId.value,
                    event.success
                )
            }
        }
    }
}
