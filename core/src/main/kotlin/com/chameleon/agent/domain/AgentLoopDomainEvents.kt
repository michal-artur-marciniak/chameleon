package com.chameleon.agent.domain

import com.chameleon.agent.domain.RunId
import com.chameleon.session.domain.SessionId
import com.chameleon.session.domain.SessionKey
import java.time.Instant

/**
 * Domain events emitted by the agent lifecycle.
 * These represent significant business events in the agent lifecycle.
 */
sealed interface AgentLoopDomainEvent {
    val runId: RunId
    val timestamp: Instant
    
    /**
     * Emitted when an agent run starts
     */
    data class AgentLoopStarted(
        override val runId: RunId,
        val sessionId: SessionId,
        val agentId: String,
        val sessionKey: SessionKey,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
    
    /**
     * Emitted when a tool call is initiated by the LLM
     */
    data class ToolCallInitiated(
        override val runId: RunId,
        val toolName: String,
        val toolCallId: String,
        val argumentsJson: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
    
    /**
     * Emitted when a tool execution completes
     */
    data class ToolExecuted(
        override val runId: RunId,
        val toolName: String,
        val toolCallId: String,
        val success: Boolean,
        val durationMs: Long,
        val resultSummary: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
    
    /**
     * Emitted when an assistant response is generated and persisted
     */
    data class ResponseGenerated(
        override val runId: RunId,
        val messageId: String,
        val tokenCount: Int? = null,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
    
    /**
     * Emitted when a user message is added to the session
     */
    data class MessageAdded(
        override val runId: RunId,
        val sessionId: SessionId,
        val role: String,
        val messageId: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
    
    /**
     * Emitted when an LLM completion is requested
     */
    data class LlmCompletionRequested(
        override val runId: RunId,
        val providerId: String,
        val modelId: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
    
    /**
     * Emitted when an LLM completion is received
     */
    data class LlmCompletionReceived(
        override val runId: RunId,
        val providerId: String,
        val modelId: String,
        val completionTokens: Int? = null,
        val promptTokens: Int? = null,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
    
    /**
     * Emitted when an LLM error occurs
     */
    data class LlmError(
        override val runId: RunId,
        val providerId: String,
        val modelId: String,
        val error: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
    
    /**
     * Emitted when an agent run completes (success or error)
     */
    data class AgentLoopCompleted(
        override val runId: RunId,
        val sessionId: SessionId,
        val success: Boolean,
        val error: String? = null,
        override val timestamp: Instant = Instant.now()
    ) : AgentLoopDomainEvent
}
