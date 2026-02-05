package agent.platform.tool

import kotlinx.serialization.json.JsonObject

data class ToolDefinition(
    val name: String,
    val description: String,
    val schema: JsonObject
)

data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class ToolResult(
    val content: String,
    val isError: Boolean = false
)
