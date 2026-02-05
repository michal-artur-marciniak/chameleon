package agent.platform.tool

/**
 * Domain events for tool registry operations.
 */
sealed class ToolDomainEvent {
    abstract val toolName: String
    abstract val timestamp: Long
    
    /**
     * Emitted when a tool execution is denied by policy.
     */
    data class ToolPolicyViolation(
        override val toolName: String,
        val toolCallId: String,
        val reason: String,
        val policyAction: PolicyAction,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ToolDomainEvent()
    
    /**
     * Emitted when a tool is registered.
     */
    data class ToolRegistered(
        override val toolName: String,
        val description: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ToolDomainEvent()
    
    /**
     * Emitted when a tool is unregistered.
     */
    data class ToolUnregistered(
        override val toolName: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ToolDomainEvent()
}

/**
 * Policy action taken when a tool execution is evaluated.
 */
enum class PolicyAction {
    ALLOWED,
    DENIED,
    REQUIRES_APPROVAL
}
