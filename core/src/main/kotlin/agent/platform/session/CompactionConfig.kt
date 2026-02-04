package agent.platform.session

import kotlinx.serialization.Serializable

@Serializable
data class CompactionConfig(
    val reserveTokensFloor: Int = 20000,
    val softThresholdTokens: Int = 4000,
    val memoryFlush: MemoryFlushConfig = MemoryFlushConfig()
)

@Serializable
data class MemoryFlushConfig(
    val enabled: Boolean = true
)
