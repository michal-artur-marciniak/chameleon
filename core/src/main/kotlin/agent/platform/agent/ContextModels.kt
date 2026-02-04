package agent.platform.agent

data class ContextBundle(
    val systemPrompt: String,
    val messages: List<agent.platform.session.Message>,
    val toolSchemasJson: String,
    val report: SystemPromptReport
)

data class SystemPromptReport(
    val totalChars: Int,
    val injectedFiles: List<InjectedFileReport>,
    val toolSchemaChars: Int
)

data class InjectedFileReport(
    val path: String,
    val rawChars: Int,
    val injectedChars: Int,
    val truncated: Boolean
)
