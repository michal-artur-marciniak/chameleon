package agent.platform.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * In-memory implementation of ToolRegistry with policy enforcement.
 * 
 * This adapter:
 * - Stores tool definitions
 * - Validates tool existence
 * - Enforces allow/deny/ask policies via ToolPolicyService
 * - Emits domain events for policy violations
 */
class InMemoryToolRegistry(
    private val tools: List<ToolDefinition> = emptyList(),
    private val policyService: ToolPolicyService = ToolPolicyService(ToolsConfig()),
    private val onDomainEvent: ((ToolDomainEvent) -> Unit)? = null
) : ToolRegistry {
    private val byName = tools.associateBy { it.name }

    override fun list(): List<ToolDefinition> = tools

    override fun get(name: String): ToolDefinition? = byName[name]
    
    override fun isRegistered(name: String): Boolean = name in byName

    override fun validatePolicy(call: ToolCallRequest): ToolPolicyService.PolicyDecision {
        // First check if tool exists
        if (!isRegistered(call.name)) {
            return ToolPolicyService.PolicyDecision.Deny("Tool '${call.name}' not found in registry")
        }
        
        // Check if this is the exec tool
        val isExecTool = call.name == "exec"
        
        // Extract exec command if applicable
        val execCommand = if (isExecTool) {
            extractExecCommand(call.argumentsJson)
        } else null
        
        return policyService.evaluate(call.name, isExecTool, execCommand)
    }

    override suspend fun execute(call: ToolCallRequest): ToolResult {
        // 1. Validate tool exists
        val toolDef = get(call.name)
            ?: return ToolResult(
                content = "Error: Tool '${call.name}' not found in registry",
                isError = true
            )

        // 2. Validate policy
        val policyDecision = validatePolicy(call)
        
        when (policyDecision) {
            is ToolPolicyService.PolicyDecision.Deny -> {
                // Emit domain event for policy violation
                onDomainEvent?.invoke(
                    ToolDomainEvent.ToolPolicyViolation(
                        toolName = call.name,
                        toolCallId = call.id,
                        reason = policyDecision.reason,
                        policyAction = PolicyAction.DENIED
                    )
                )
                return ToolResult(
                    content = "Error: ${policyDecision.reason}",
                    isError = true
                )
            }
            is ToolPolicyService.PolicyDecision.Ask -> {
                // Emit domain event for approval required
                onDomainEvent?.invoke(
                    ToolDomainEvent.ToolPolicyViolation(
                        toolName = call.name,
                        toolCallId = call.id,
                        reason = policyDecision.reason,
                        policyAction = PolicyAction.REQUIRES_APPROVAL
                    )
                )
                return ToolResult(
                    content = "Approval required: ${policyDecision.reason}",
                    isError = true
                )
            }
            is ToolPolicyService.PolicyDecision.Allow -> {
                // Continue to execution
            }
        }

        // 3. Execute the tool (placeholder - actual implementation would dispatch to tool handlers)
        return try {
            executeTool(call)
        } catch (e: Exception) {
            ToolResult(
                content = "Error executing tool '${call.name}': ${e.message}",
                isError = true
            )
        }
    }
    
    /**
     * Extracts the command from exec tool arguments.
     */
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
    
    /**
     * Placeholder for actual tool execution.
     * In a full implementation, this would dispatch to specific tool handlers.
     */
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
    
    /**
     * Executes the exec tool with safe bin validation.
     */
    private fun executeExecTool(call: ToolCallRequest): ToolResult {
        val command = extractExecCommand(call.argumentsJson)
            ?: return ToolResult(
                content = "Error: No command provided for exec tool",
                isError = true
            )
        
        // Safe bins validation already happened in policy check
        // This is where the actual command execution would happen
        return ToolResult(
            content = "Exec tool would execute: $command (actual execution not yet implemented)",
            isError = false
        )
    }
}
