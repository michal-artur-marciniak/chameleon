package agent.platform.tool.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ToolsConfig(
    val exec: ExecToolConfig = ExecToolConfig(),
    val allow: List<String> = listOf("read", "write", "edit", "exec", "memory_search", "memory_get"),
    val deny: List<String> = emptyList()
)

@Serializable
data class ExecToolConfig(
    val security: ExecSecurity = ExecSecurity.ALLOWLIST,
    val ask: AskMode = AskMode.ON_MISS,
    val safeBins: List<String> = listOf("jq", "grep", "cut", "sort", "uniq", "head", "tail", "tr", "wc")
)

@Serializable
enum class ExecSecurity {
    @SerialName("deny")
    DENY,
    @SerialName("allowlist")
    ALLOWLIST,
    @SerialName("full")
    FULL
}

@Serializable
enum class AskMode {
    @SerialName("off")
    OFF,
    @SerialName("on-miss")
    ON_MISS,
    @SerialName("always")
    ALWAYS
}
