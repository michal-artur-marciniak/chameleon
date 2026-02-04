# Agent Module Conventions

## Domain Aggregate Pattern

The AgentLoop aggregate lives in `domain/AgentLoop.kt` and owns the `runTurn()` orchestration method.

### Key Patterns

**1. Domain Aggregate with Factory Method**
```kotlin
// Create via factory method
val agentLoop = AgentLoop.create(agentId)

// Execute a turn with dependencies passed in
agentLoop.runTurn(runId, session, userMessage, deps).collect { event ->
    // Handle turn events
}
```

**2. TurnDependencies Pattern**
Domain aggregates receive infrastructure dependencies via a data class to keep them pure:
```kotlin
data class TurnDependencies(
    val sessionRepository: SessionRepository,
    val toolRegistry: ToolRegistry,
    val llmProvider: LlmProviderPort,
    val contextBundle: ContextBundle,
    val eventPublisher: DomainEventPublisherPort? = null
)
```

**3. Two Types of Events**
- **TurnEvent** - Synchronous events for the caller to consume (AssistantDelta, ToolStarted, etc.)
- **DomainEvent** - Asynchronous events for observability via DomainEventPublisherPort

**4. Tool Validation**
Tool calls are validated through ToolRegistry before execution:
```kotlin
// Returns null if tool not found - domain emits ToolValidationError
val toolDef = toolRegistry.get(event.name)
```

## Infrastructure Adapter Pattern

`DefaultAgentLoop` (infra) acts as an adapter that:
1. Resolves provider/model references
2. Creates the domain aggregate
3. Maps domain TurnEvents to infra AgentEvents
4. Wires concrete repository implementations to domain ports

## File Organization

- `core/src/main/kotlin/agent/platform/agent/domain/` - Domain aggregates and domain events
- `infra/src/main/kotlin/agent/platform/agent/` - Infrastructure adapters (DefaultAgentLoop, etc.)
- `app/src/main/kotlin/agent/platform/application/` - Application use cases

## When Modifying AgentLoop

1. Business logic belongs in the domain aggregate
2. Infrastructure concerns (provider resolution, etc.) stay in DefaultAgentLoop
3. Always validate tools via ToolRegistry before execution
4. Persist assistant responses to SessionRepository
5. Emit domain events via DomainEventPublisherPort for observability
