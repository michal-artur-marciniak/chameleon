package agent.platform.tool.domain

/**
 * Domain service for evaluating tool execution policies.
 * 
 * Implements allow/deny/ask semantics for tool execution based on:
 * - Global allow/deny lists from configuration
 * - Tool-specific policy rules
 * - Exec tool security modes (deny, allowlist, full)
 */
class ToolPolicyService(
    private val config: ToolsConfig
) {
    /**
     * Policy decision result for a tool execution request.
     */
    sealed class PolicyDecision {
        /**
         * Tool execution is permitted.
         */
        data class Allow(val reason: String? = null) : PolicyDecision()
        
        /**
         * Tool execution is denied.
         */
        data class Deny(val reason: String) : PolicyDecision()
        
        /**
         * Tool execution requires approval/confirmation.
         */
        data class Ask(val reason: String) : PolicyDecision()
    }
    
    /**
     * Evaluates the policy for a tool call.
     * 
     * @param toolName The name of the tool to execute
     * @param isExecTool Whether this is the exec tool (requires special handling)
     * @param execCommand The command being executed (only for exec tool)
     * @return PolicyDecision indicating allow, deny, or ask
     */
    fun evaluate(
        toolName: String,
        isExecTool: Boolean = false,
        execCommand: String? = null
    ): PolicyDecision {
        // 1. Check deny list first (deny takes precedence)
        if (toolName in config.deny) {
            return PolicyDecision.Deny("Tool '$toolName' is in deny list")
        }
        
        // 2. Check if tool is in allow list
        val isAllowed = toolName in config.allow
        
        // 3. Handle exec tool specially based on security mode
        if (isExecTool && isAllowed) {
            return evaluateExecPolicy(execCommand)
        }
        
        // 4. Non-exec tools: if in allow list, allow; otherwise ask
        return if (isAllowed) {
            PolicyDecision.Allow()
        } else {
            when (config.exec.ask) {
                AskMode.OFF -> PolicyDecision.Deny("Tool '$toolName' not in allow list and ask mode is OFF")
                AskMode.ON_MISS -> PolicyDecision.Ask("Tool '$toolName' not in allow list")
                AskMode.ALWAYS -> PolicyDecision.Ask("Ask mode is ALWAYS")
            }
        }
    }
    
    /**
     * Evaluates the exec tool policy based on security configuration.
     */
    private fun evaluateExecPolicy(execCommand: String?): PolicyDecision {
        return when (config.exec.security) {
            ExecSecurity.DENY -> {
                PolicyDecision.Deny("Exec tool is disabled (security mode: DENY)")
            }
            ExecSecurity.ALLOWLIST -> {
                if (execCommand == null) {
                    PolicyDecision.Ask("Exec command not provided for allowlist validation")
                } else {
                    val command = execCommand.trim().split(" ").firstOrNull() ?: ""
                    if (command in config.exec.safeBins) {
                        PolicyDecision.Allow("Command '$command' is in safe bins list")
                    } else {
                        when (config.exec.ask) {
                            AskMode.OFF -> PolicyDecision.Deny(
                                "Command '$command' not in safe bins and ask mode is OFF"
                            )
                            AskMode.ON_MISS, AskMode.ALWAYS -> PolicyDecision.Ask(
                                "Command '$command' not in safe bins list"
                            )
                        }
                    }
                }
            }
            ExecSecurity.FULL -> {
                PolicyDecision.Allow("Exec tool in FULL security mode")
            }
        }
    }
    
    /**
     * Checks if a tool is explicitly allowed.
     */
    fun isToolAllowed(toolName: String): Boolean {
        return toolName in config.allow && toolName !in config.deny
    }
    
    /**
     * Gets the list of safe binaries for exec tool.
     */
    fun getSafeBins(): List<String> = config.exec.safeBins
    
    /**
     * Checks if the given command is in the safe bins list.
     */
    fun isSafeBin(command: String): Boolean {
        val cmd = command.trim().split(" ").firstOrNull() ?: ""
        return cmd in config.exec.safeBins
    }
}
