package agent.platform.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionKey(
    val agentId: String,
    val channel: String,
    val peerType: PeerType,
    val peerId: String,
    val threadId: String? = null
) {
    fun toKeyString(): String = buildString {
        append("agent:$agentId:$channel:${peerType.name.lowercase()}:$peerId")
        threadId?.let { append(":thread:$it") }
    }

    companion object {
        fun parse(key: String): SessionKey {
            val parts = key.split(":")
            require(parts.size >= 5 && parts[0] == "agent") { "Invalid session key: $key" }
            val threadId = if (parts.size >= 7 && parts[5] == "thread") parts[6] else null
            return SessionKey(
                agentId = parts[1],
                channel = parts[2],
                peerType = PeerType.valueOf(parts[3].uppercase()),
                peerId = parts[4],
                threadId = threadId
            )
        }
    }
}

@Serializable
enum class PeerType {
    @SerialName("dm")
    DM,
    @SerialName("group")
    GROUP,
    @SerialName("channel")
    CHANNEL
}
