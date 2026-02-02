package agent.sdk

interface ChannelPort {
    val id: String
    suspend fun start(handler: suspend (InboundMessage) -> Unit)
    suspend fun send(message: OutboundMessage): Result<Unit>
    suspend fun stop()
}

data class InboundMessage(
    val channelId: String,
    val chatId: String,
    val userId: String,
    val text: String,
    val isGroup: Boolean,
    val isMentioned: Boolean
)

data class OutboundMessage(
    val channelId: String,
    val chatId: String,
    val text: String
)
