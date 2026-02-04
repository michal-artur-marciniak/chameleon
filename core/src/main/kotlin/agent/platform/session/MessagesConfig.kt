package agent.platform.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessagesConfig(
    val groupChat: GroupChatConfig = GroupChatConfig(),
    val dm: DmConfig = DmConfig(),
    val queue: QueueConfig = QueueConfig()
)

@Serializable
data class GroupChatConfig(
    val historyLimit: Int = 50,
    val mentionPatterns: List<String> = emptyList()
)

@Serializable
data class DmConfig(
    val historyLimit: Int = 100
)

@Serializable
data class QueueConfig(
    val mode: QueueMode = QueueMode.SEQUENTIAL,
    val cap: Int = 10,
    val debounceMs: Int = 100,
    val maxConcurrent: Int = 1
)

@Serializable
enum class QueueMode {
    @SerialName("sequential")
    SEQUENTIAL,
    @SerialName("parallel")
    PARALLEL,
    @SerialName("steer")
    STEER,
    @SerialName("drop")
    DROP
}
