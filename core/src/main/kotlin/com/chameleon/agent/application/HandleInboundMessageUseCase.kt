package com.chameleon.agent.application

import com.chameleon.agent.AgentEvent
import com.chameleon.agent.AgentRunRequest
import com.chameleon.agent.AgentRuntime
import com.chameleon.agent.Phase
import com.chameleon.session.domain.SessionKey
import com.chameleon.sdk.ChannelPort
import com.chameleon.sdk.InboundMessage
import com.chameleon.sdk.OutboundMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * Use Case: UC-001 - HandleInboundMessageUseCase
 *
 * Orchestrates the handling of inbound channel messages through the DDD agent runtime.
 *
 * Responsibilities:
 * - Maps inbound messages to SessionKeys
 * - Starts agent runs via the AgentRuntime
 * - Streams lifecycle and assistant events
 * - Handles response buffering and outbound message delivery
 *
 * This is the application layer entry point for channel message processing,
 * keeping channel adapters thin and pushing orchestration to the application layer.
 *
 * @property agentRuntime The runtime for starting agent runs
 */
class HandleInboundMessageUseCase(
    private val agentRuntime: AgentRuntime
) {
    private val logger = LoggerFactory.getLogger(HandleInboundMessageUseCase::class.java)
    
    /**
     * Handles an inbound message from a channel.
     * 
     * @param channel The channel port that received the message
     * @param inbound The inbound message
     * @param agentId The agent ID to handle this message
     * @return Flow of agent events for this run
     */
    fun execute(
        channel: ChannelPort,
        inbound: InboundMessage,
        agentId: String
    ): Flow<AgentEvent> = flow {
        logger.info(
            "[UC-001] Handling inbound message: channel={}, chatId={}, userId={}, agentId={}",
            channel.id,
            inbound.chatId,
            inbound.userId,
            agentId
        )
        
        // 1. Resolve session key from inbound message
        val sessionKey = buildSessionKey(channel, inbound, agentId)
        logger.debug("[UC-001] Resolved session key: {}", sessionKey.toKeyString())
        
        // 2. Start agent run
        val request = AgentRunRequest(
            sessionKey = sessionKey,
            inbound = inbound,
            agentId = agentId
        )
        
        val handle = agentRuntime.start(request)
        logger.debug("[UC-001] Agent run started: runId={}", handle.runId.value)
        
        // 3. Stream lifecycle + assistant events
        handle.events.collect { event ->
            emit(event)
        }
        
        logger.debug("[UC-001] Agent run completed: runId={}", handle.runId.value)
    }
    
    /**
     * Handles an inbound message and sends the response back through the channel.
     * This is a convenience method for simple channel integrations.
     * 
     * @param channel The channel port
     * @param inbound The inbound message
     * @param agentId The agent ID
     */
    suspend fun executeAndRespond(
        channel: ChannelPort,
        inbound: InboundMessage,
        agentId: String
    ) {
        val handle = agentRuntime.start(
            AgentRunRequest(
                sessionKey = buildSessionKey(channel, inbound, agentId),
                inbound = inbound,
                agentId = agentId
            )
        )
        
        var responseBuffer = StringBuilder()
        
        handle.events.collect { event ->
            when (event) {
                is AgentEvent.AssistantDelta -> {
                    if (!event.done) {
                        responseBuffer.append(event.text)
                    }
                }
                is AgentEvent.Lifecycle -> {
                    if (event.phase == Phase.END || event.phase == Phase.ERROR) {
                        val reply = responseBuffer.toString().trim()
                        if (reply.isNotBlank()) {
                            channel.send(
                                OutboundMessage(
                                    channelId = inbound.channelId,
                                    chatId = inbound.chatId,
                                    text = reply
                                )
                            ).onFailure { error ->
                                logger.error("[UC-001] Failed to send response: {}", error.message)
                            }
                        }
                        responseBuffer = StringBuilder()
                    }
                }
                else -> Unit
            }
        }
    }
    
    /**
     * Builds a SessionKey from channel and inbound message.
     *
     * Determines peer type based on whether the message is from a group chat.
     *
     * @param channel The channel port
     * @param inbound The inbound message with chat metadata
     * @param agentId The agent ID assigned to handle this conversation
     * @return SessionKey identifying this conversation uniquely
     */
    private fun buildSessionKey(
        channel: ChannelPort,
        inbound: InboundMessage,
        agentId: String
    ): SessionKey {
        return SessionKey(
            agentId = agentId,
            channel = channel.id,
            peerType = if (inbound.isGroup) {
                com.chameleon.session.domain.PeerType.GROUP
            } else {
                com.chameleon.session.domain.PeerType.DM
            },
            peerId = inbound.chatId
        )
    }
    
}
