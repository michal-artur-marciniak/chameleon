package agent.platform.tool.port

import agent.platform.tool.domain.ToolCallRequest
import agent.platform.tool.domain.ToolDefinition
import agent.platform.tool.domain.ToolPolicyService
import agent.platform.tool.domain.ToolResult

/**
 * Port for tool definition lookup.
 */
interface ToolDefinitionRegistry {
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
     * Checks if a tool exists in the registry.
     */
    fun isRegistered(name: String): Boolean
}

/**
 * Port for tool policy evaluation.
 */
interface ToolPolicyEvaluator {
    /**
     * Validates if a tool call is allowed by policy without executing.
     *
     * @param call The tool call request
     * @return Policy decision (Allow, Deny, or Ask)
     */
    fun validatePolicy(call: ToolCallRequest): ToolPolicyService.PolicyDecision
}

/**
 * Port for tool execution.
 */
interface ToolExecutor {
    /**
     * Executes a tool call.
     *
     * @param call The tool call request
     * @return ToolResult containing output or error
     */
    suspend fun execute(call: ToolCallRequest): ToolResult
}
