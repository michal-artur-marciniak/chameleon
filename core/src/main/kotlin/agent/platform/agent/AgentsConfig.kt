package agent.platform.agent

import agent.platform.session.domain.CompactionConfig
import kotlinx.serialization.Serializable

@Serializable
data class AgentsConfig(
    val defaults: AgentDefaultsConfig = AgentDefaultsConfig(),
    val list: List<AgentConfig> = listOf(AgentConfig(id = "main", default = true))
)

@Serializable
data class AgentDefaultsConfig(
    val model: AgentModelConfig = AgentModelConfig(),
    val workspace: String = "/app/workspace",
    val extensionsDir: String = "/app/extensions",
    val contextTokens: Int = 128000,
    val timeoutSeconds: Int = 600,
    val thinkingDefault: ThinkingLevel = ThinkingLevel.OFF,
    val verboseDefault: VerboseLevel = VerboseLevel.OFF,
    val compaction: CompactionConfig = CompactionConfig()
)

@Serializable
data class AgentConfig(
    val id: String,
    val default: Boolean = false,
    val name: String? = null,
    val workspace: String? = null,
    val extensionsDir: String? = null,
    val model: AgentModelConfig? = null,
    val compaction: CompactionConfig? = null
)
