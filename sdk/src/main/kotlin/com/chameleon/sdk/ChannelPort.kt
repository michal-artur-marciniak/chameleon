package com.chameleon.sdk

/**
 * Port interface for channel integrations.
 * Channels provide bidirectional communication with external messaging platforms.
 * Implementations are loaded as plugins and instantiated by the channel adapter.
 */
interface ChannelPort {
    /** Unique identifier for the channel type (e.g., "telegram", "slack") */
    val id: String

    /**
     * Start the channel and begin listening for inbound messages.
     * Should block until [stop] is called or an unrecoverable error occurs.
     * @param handler Callback invoked for each received message
     */
    suspend fun start(handler: suspend (InboundMessage) -> Unit)

    /**
     * Send an outbound message to the channel.
     * @param message The message to send
     * @return Success or failure result
     */
    suspend fun send(message: OutboundMessage): Result<Unit>

    /** Stop the channel and clean up resources */
    suspend fun stop()
}

/**
 * Represents a message received from a channel.
 * @property channelId The channel that received the message
 * @property chatId The conversation identifier (chat, channel, thread)
 * @property userId The sender's unique identifier
 * @property text The message content
 * @property isGroup True if the message came from a group/room context
 * @property isMentioned True if the bot was explicitly mentioned in the message
 */
data class InboundMessage(
    val channelId: String,
    val chatId: String,
    val userId: String,
    val text: String,
    val isGroup: Boolean,
    val isMentioned: Boolean
)

/**
 * Represents a message to be sent to a channel.
 * @property channelId The target channel
 * @property chatId The target conversation
 * @property text The message content to send
 */
data class OutboundMessage(
    val channelId: String,
    val chatId: String,
    val text: String
)
