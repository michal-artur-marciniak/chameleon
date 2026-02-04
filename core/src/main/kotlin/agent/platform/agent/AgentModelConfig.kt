package agent.platform.agent

import kotlinx.serialization.Serializable

@Serializable
data class AgentModelConfig(
    val primary: String = "kimi/kimi-k2.5",
    val fallbacks: List<String> = emptyList()
)