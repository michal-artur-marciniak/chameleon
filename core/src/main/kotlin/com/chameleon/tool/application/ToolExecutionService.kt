package com.chameleon.tool.application

import com.chameleon.tool.domain.ToolCallRequest
import com.chameleon.tool.domain.ToolPolicyService
import com.chameleon.tool.domain.ToolResult
import com.chameleon.tool.port.ToolDefinitionRegistry
import com.chameleon.tool.port.ToolExecutor
import com.chameleon.tool.port.ToolPolicyEvaluator

/**
 * Service for executing tool calls with validation and policy enforcement.
 *
 * Execution flow:
 * 1. Validate tool exists in registry
 * 2. Evaluate policy (Allow/Deny/Ask)
 * 3. Execute via [ToolExecutor] if allowed
 *
 * @property toolDefinitionRegistry Registry of available tool definitions
 * @property toolPolicyEvaluator Evaluates policy decisions for tool calls
 * @property toolExecutor Executes the actual tool calls
 */
class ToolExecutionService(
    private val toolDefinitionRegistry: ToolDefinitionRegistry,
    private val toolPolicyEvaluator: ToolPolicyEvaluator,
    private val toolExecutor: ToolExecutor
) {

    /**
     * Executes a tool call with validation and policy checks.
     *
     * @param call The tool call request containing tool name and arguments
     * @return Tool result containing output content or error message
     */
    suspend fun execute(call: ToolCallRequest): ToolResult {
        if (!toolDefinitionRegistry.isRegistered(call.name)) {
            return ToolResult(
                content = "Error: Tool '${call.name}' not found in registry",
                isError = true
            )
        }

        when (val policyDecision = toolPolicyEvaluator.validatePolicy(call)) {
            is ToolPolicyService.PolicyDecision.Deny -> {
                return ToolResult(
                    content = "Error: ${policyDecision.reason}",
                    isError = true
                )
            }
            is ToolPolicyService.PolicyDecision.Ask -> {
                return ToolResult(
                    content = "Approval required: ${policyDecision.reason}",
                    isError = true
                )
            }
            is ToolPolicyService.PolicyDecision.Allow -> Unit
        }

        return try {
            toolExecutor.execute(call)
        } catch (e: Exception) {
            ToolResult(
                content = "Error executing tool '${call.name}': ${e.message}",
                isError = true
            )
        }
    }
}
