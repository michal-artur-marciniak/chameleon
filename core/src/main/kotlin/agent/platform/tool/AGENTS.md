# Tool Module Conventions

## Domain Service Pattern

The `ToolPolicyService` lives in the tool module and evaluates allow/deny/ask policies for tool execution.

### Key Patterns

**1. Policy Evaluation**
```kotlin
val policyService = ToolPolicyService(config)
val decision = policyService.evaluate(
    toolName = "exec",
    isExecTool = true,
    execCommand = "jq '.' file.json"
)

when (decision) {
    is PolicyDecision.Allow -> execute()
    is PolicyDecision.Deny -> return error
    is PolicyDecision.Ask -> request approval
}
```

**2. Policy Precedence**
- Deny list takes precedence over allow list
- Unknown tools follow ask mode (OFF = deny, ON_MISS = ask, ALWAYS = ask)
- Exec tool has additional security modes: DENY, ALLOWLIST, FULL

**3. Registry Integration**
Tool adapters implement definition lookup, policy evaluation, and execution:
```kotlin
val definitions = InMemoryToolDefinitionRegistry(toolDefinitions)
val policy = ToolPolicyEvaluatorAdapter(definitions, policyService)
val executor = ToolExecutorAdapter(definitions) { event -> publish(event) }

// Policy is checked before execution
val decision = policy.validatePolicy(toolCall)
val result = executor.execute(toolCall)
```

## Configuration

ToolsConfig controls policy behavior:

```kotlin
ToolsConfig(
    allow = listOf("read", "write", "edit", "exec"),  // Whitelist
    deny = listOf("dangerous_tool"),                   // Blacklist (takes precedence)
    exec = ExecToolConfig(
        security = ExecSecurity.ALLOWLIST,  // DENY, ALLOWLIST, or FULL
        ask = AskMode.ON_MISS,              // OFF, ON_MISS, or ALWAYS
        safeBins = listOf("jq", "grep")     // For ALLOWLIST mode
    )
)
```

## Domain Events

Tool operations emit domain events:

- `ToolPolicyViolation` - Tool execution denied or requires approval
- `ToolRegistered` - New tool added to registry
- `ToolUnregistered` - Tool removed from registry

## Invariants

1. **Deny takes precedence** - A tool in both allow and deny lists is denied
2. **Unknown tools require approval** - Tools not in allow list trigger ask mode
3. **Exec tool is special** - Has additional security layers beyond basic policy
4. **Safe bins validation** - In ALLOWLIST mode, only configured binaries allowed

## File Organization

- `ToolRegistry.kt` - Ports (definition, policy, execution)
- `ToolPolicyService.kt` - Domain service for policy evaluation
- `ToolDomainEvents.kt` - Domain events for tool operations
- `ToolsConfig.kt` - Configuration data classes
- `InMemoryToolDefinitionRegistry.kt` - Infra adapter for tool definitions
- `ToolPolicyEvaluatorAdapter.kt` - Infra adapter for policy checks
- `ToolExecutorAdapter.kt` - Infra adapter for execution

## When Modifying Tool Module

1. Policy evaluation belongs in `ToolPolicyService` (domain layer)
2. Policy enforcement belongs in `ToolPolicyEvaluatorAdapter` (infra layer)
3. Always emit `ToolPolicyViolation` events for deny/ask decisions
4. Update `ToolsConfig` if adding new policy options
5. Test policy edge cases in `ToolPolicyServiceTest.kt`

## Testing Policies

Key test scenarios:
- Tool in allow list → Allow
- Tool in deny list → Deny (even if in allow list)
- Unknown tool with ON_MISS → Ask
- Unknown tool with OFF → Deny
- Exec with DENY security → Always deny
- Exec with ALLOWLIST and safe bin → Allow
- Exec with ALLOWLIST and unsafe bin → Ask/Deny based on ask mode
