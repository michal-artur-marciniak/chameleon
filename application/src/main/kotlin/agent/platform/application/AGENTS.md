# Application Layer Conventions

## Use Case Pattern

Use cases in this application follow the DDD application layer pattern:

- **Location**: `app/src/main/kotlin/agent/platform/application/`
- **Naming**: `{Action}{Entity}UseCase.kt` (e.g., `HandleInboundMessageUseCase`)
- **Responsibilities**:
  - Orchestrate domain objects to fulfill user stories
  - Coordinate between different bounded contexts
  - Publish domain events via `DomainEventPublisherPort`
  - Keep channel adapters thin

## HandleInboundMessageUseCase (UC-001)

This use case routes inbound channel messages through the DDD agent runtime:

```kotlin
// Usage
val useCase = HandleInboundMessageUseCase(agentRuntime, eventPublisher)
useCase.execute(channel, inboundMessage, agentId)
    .collect { event -> /* handle events */ }
```

Key behaviors:
- Maps `InboundMessage` to `SessionKey` using channel metadata
- Starts agent runs via `AgentRuntime`
- Streams lifecycle events (START, END, ERROR)
- Optionally publishes domain events for observability
- Provides `executeAndRespond()` for simple channel integrations

## Domain Event Publishing

To enable domain event observability:

1. Implement `DomainEventPublisherPort` (from `core` module)
2. Pass to use case constructor: `HandleInboundMessageUseCase(runtime, publisher)`
3. The `LoggingDomainEventPublisher` is available in `infra` for basic logging

## Channel Adapter Contract

Channel adapters (like TelegramPlugin) should:
1. Implement `ChannelPort` interface from `sdk` module
2. Be thin - delegate to use cases
3. Not contain business logic
4. Handle protocol-specific concerns only
