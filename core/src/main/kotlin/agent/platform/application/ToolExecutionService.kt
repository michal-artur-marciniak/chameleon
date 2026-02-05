package agent.platform.application

import agent.platform.tool.ToolCallRequest
import agent.platform.tool.ToolDefinitionRegistry
import agent.platform.tool.ToolExecutor
import agent.platform.tool.ToolPolicyEvaluator
import agent.platform.tool.ToolPolicyService
import agent.platform.tool.ToolResult

class ToolExecutionService(
    private val toolDefinitionRegistry: ToolDefinitionRegistry,
    private val toolPolicyEvaluator: ToolPolicyEvaluator,
    private val toolExecutor: ToolExecutor
) {
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
