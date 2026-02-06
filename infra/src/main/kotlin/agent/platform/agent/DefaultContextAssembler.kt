package agent.platform.agent

import agent.platform.config.PlatformConfig
import agent.platform.session.domain.Message
import agent.platform.session.domain.MessageRole
import agent.platform.session.domain.Session
import agent.platform.tool.port.ToolDefinitionRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DefaultContextAssembler(
    private val config: PlatformConfig
) : ContextAssembler {
    private val workspace = Path.of(config.agents.defaults.workspace)
    private val bootstrapFiles = listOf(
        "AGENTS.md",
        "SOUL.md",
        "TOOLS.md",
        "IDENTITY.md",
        "USER.md",
        "HEARTBEAT.md",
        "BOOTSTRAP.md"
    )

    override fun build(session: Session, tools: ToolDefinitionRegistry): ContextBundle {
        val injected = mutableListOf<InjectedFileReport>()
        val projectContext = StringBuilder()

        bootstrapFiles.forEach { name ->
            val path = workspace.resolve(name)
            if (!Files.exists(path)) {
                injected.add(InjectedFileReport(path.toString(), 0, 0, false))
                return@forEach
            }
            val raw = Files.readString(path)
            val rawChars = raw.length
            val maxChars = 20000
            val injectedText = if (rawChars > maxChars) {
                raw.take(maxChars) + "\n\n[TRUNCATED]"
            } else {
                raw
            }
            injected.add(
                InjectedFileReport(
                    path = path.toString(),
                    rawChars = rawChars,
                    injectedChars = injectedText.length,
                    truncated = rawChars > maxChars
                )
            )
            if (injectedText.isNotBlank()) {
                projectContext.append("\n\n# ").append(name).append("\n").append(injectedText)
            }
        }

        val toolSchemasJson = tools.list().joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { it.schema.toString() }

        val systemPrompt = buildSystemPrompt(toolSchemasJson, projectContext.toString())
        val report = SystemPromptReport(
            totalChars = systemPrompt.length,
            injectedFiles = injected,
            toolSchemaChars = toolSchemasJson.length
        )

        val history = session.messages
        return ContextBundle(
            systemPrompt = systemPrompt,
            messages = history,
            toolSchemasJson = toolSchemasJson,
            report = report
        )
    }

    private fun buildSystemPrompt(toolSchemasJson: String, projectContext: String): String {
        val now = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault())
            .format(java.time.Instant.now())
        return buildString {
            append("You are the agent runtime.\n")
            append("Workspace: ").append(workspace.toString()).append("\n")
            append("Current Time: ").append(now).append("\n")
            append("\nTool Schemas (JSON):\n").append(toolSchemasJson)
            if (projectContext.isNotBlank()) {
                append("\n\nProject Context:\n").append(projectContext)
            }
        }
    }
}
