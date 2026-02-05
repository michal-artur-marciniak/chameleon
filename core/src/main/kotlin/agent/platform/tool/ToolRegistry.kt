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

/**
 * Port for tool registry operations.
 * 
 * The registry is responsible for:
 * - Storing tool definitions
 * - Validating tool existence
 * - Executing tool calls with policy enforcement
 */
interface ToolRegistry {
    /**
     * Lists all registered tools.
     */
    fun list(): List<ToolDefinition>
    
    /**
     * Gets a tool definition by name.
     * @return The tool definition or null if not found
     */
    fun get(name: String): ToolDefinition?
    
    /**
     * Executes a tool call with validation and policy enforcement.
     * 
     * @param call The tool call request
     * @return ToolResult containing output or error
     */
    suspend fun execute(call: ToolCallRequest): ToolResult
    
    /**
     * Validates if a tool call is allowed by policy without executing.
     * 
     * @param call The tool call request
     * @return Policy decision (Allow, Deny, or Ask)
     */
    fun validatePolicy(call: ToolCallRequest): ToolPolicyService.PolicyDecision
    
    /**
     * Checks if a tool exists in the registry.
     */
    fun isRegistered(name: String): Boolean
}
