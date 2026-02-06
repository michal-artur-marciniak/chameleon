package agent.platform.tool

import agent.platform.tool.domain.ToolCallRequest
import agent.platform.tool.domain.ToolPolicyService
import agent.platform.tool.domain.ToolsConfig
import agent.platform.tool.port.ToolDefinitionRegistry
import agent.platform.tool.port.ToolPolicyEvaluator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Policy evaluator that checks tool existence and applies ToolPolicyService rules.
 */
class ToolPolicyEvaluatorAdapter(
    private val toolRegistry: ToolDefinitionRegistry,
    private val policyService: ToolPolicyService = ToolPolicyService(ToolsConfig())
) : ToolPolicyEvaluator {
    override fun validatePolicy(call: ToolCallRequest): ToolPolicyService.PolicyDecision {
        if (!toolRegistry.isRegistered(call.name)) {
            return ToolPolicyService.PolicyDecision.Deny("Tool '${call.name}' not found in registry")
        }

        val isExecTool = call.name == "exec"
        val execCommand = if (isExecTool) extractExecCommand(call.argumentsJson) else null

        return policyService.evaluate(call.name, isExecTool, execCommand)
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
