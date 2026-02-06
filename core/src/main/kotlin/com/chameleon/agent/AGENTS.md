# Agent Module Conventions

## Domain Aggregate Pattern

The AgentLoop aggregate lives in `domain/AgentLoop.kt` and owns the decision logic for a single turn.

### Key Patterns

**1. Domain Aggregate with Factory Method**
```kotlin
// Create via factory method
val agentLoop = AgentLoop.create(agentId)

// Process a completion stream with dependencies passed in
agentLoop.processCompletion(runId, events, deps).collect { event ->
    // Handle turn events
}
```

**2. TurnDependencies Pattern**
Domain aggregates receive infrastructure dependencies via a data class to keep them pure:
```kotlin
data class TurnDependencies(
    val toolRegistry: ToolDefinitionRegistry
)
```

**3. Two Types of Events**
- **TurnEvent** - Synchronous events for the caller to consume (AssistantDelta, TurnCompleted, etc.)
- **DomainEvent** - Asynchronous events for observability via DomainEventPublisherPort

**4. Tool Validation**
Tool calls are validated through ToolDefinitionRegistry before execution:
```kotlin
// Returns null if tool not found - domain emits ToolValidationError
val toolDef = toolRegistry.get(event.name)
```

## Application Service Pattern

`AgentTurnService` (application) orchestrates a single turn:
1. Resolves provider/model references
2. Builds context and dependencies
3. Invokes the domain aggregate
4. Maps TurnEvents to AgentEvents

`DefaultAgentLoop` (infra) delegates to `AgentRunService`, which wraps `AgentTurnService`.

## File Organization

- `core/src/main/kotlin/com/chameleon/agent/domain/` - Domain aggregates and domain events
- `application/src/main/kotlin/com/chameleon/application/` - Application services (AgentRunService, AgentTurnService)
- `infra/src/main/kotlin/com/chameleon/agent/` - Infrastructure adapters (DefaultAgentLoop, etc.)
- `bootstrap/src/main/kotlin/com/chameleon/` - Bootstrap wiring and entrypoint

## When Modifying AgentLoop

1. Business logic belongs in the domain aggregate
2. Orchestration and provider resolution live in AgentTurnService
3. Always validate tools via ToolDefinitionRegistry before execution
4. Persist assistant responses and tool results in AgentTurnService
5. Emit domain events via DomainEventPublisherPort for observability
