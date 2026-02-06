package com.chameleon.tool

import com.chameleon.tool.domain.ToolCallRequest
import com.chameleon.tool.domain.ToolPolicyService
import com.chameleon.tool.domain.ToolsConfig
import com.chameleon.tool.port.ToolDefinitionRegistry
import com.chameleon.tool.port.ToolPolicyEvaluator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Policy evaluator adapter bridging [ToolPolicyService] with [ToolDefinitionRegistry].
 *
 * Validates:
 * - Tool is registered in the definition registry
 * - Tool call complies with policy rules (especially for exec tool)
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
