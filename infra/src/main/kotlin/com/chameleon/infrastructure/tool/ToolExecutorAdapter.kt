package com.chameleon.tool

import com.chameleon.tool.domain.ToolCallRequest
import com.chameleon.tool.domain.ToolDomainEvent
import com.chameleon.tool.domain.ToolResult
import com.chameleon.tool.port.ToolDefinitionRegistry
import com.chameleon.tool.port.ToolExecutor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool executor that dispatches to built-in tool handlers.
 *
 * Currently implements stub handlers for:
 * - read, write, edit: File operations (not yet implemented)
 * - exec: Command execution (stubbed)
 * - memory_search, memory_get: Memory operations (not yet implemented)
 */
class ToolExecutorAdapter(
    private val toolRegistry: ToolDefinitionRegistry,
    private val onDomainEvent: ((ToolDomainEvent) -> Unit)? = null
) : ToolExecutor {
    override suspend fun execute(call: ToolCallRequest): ToolResult {
        if (!toolRegistry.isRegistered(call.name)) {
            return ToolResult(
                content = "Error: Tool '${call.name}' not found in registry",
                isError = true
            )
        }

        return try {
            executeTool(call)
        } catch (e: Exception) {
            ToolResult(
                content = "Error executing tool '${call.name}': ${e.message}",
                isError = true
            )
        }
    }

    private fun executeTool(call: ToolCallRequest): ToolResult {
        return when (call.name) {
            "read" -> ToolResult("Reading file not yet implemented")
            "write" -> ToolResult("Writing file not yet implemented")
            "edit" -> ToolResult("Editing file not yet implemented")
            "exec" -> executeExecTool(call)
            "memory_search" -> ToolResult("Memory search not yet implemented")
            "memory_get" -> ToolResult("Memory get not yet implemented")
            else -> ToolResult(
                content = "Tool '${call.name}' execution not implemented",
                isError = true
            )
        }
    }

    private fun executeExecTool(call: ToolCallRequest): ToolResult {
        val command = extractExecCommand(call.argumentsJson)
            ?: return ToolResult(
                content = "Error: No command provided for exec tool",
                isError = true
            )

        return ToolResult(
            content = "Exec tool would execute: $command (actual execution not yet implemented)",
            isError = false
        )
    }

    private fun extractExecCommand(argumentsJson: String): String? {
        return try {
            val json = Json.parseToJsonElement(argumentsJson)
            val obj = json.jsonObject
            obj["command"]?.jsonPrimitive?.content
                ?: obj["cmd"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}
