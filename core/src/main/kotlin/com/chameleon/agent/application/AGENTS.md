# Agent Application Layer

Orchestrates agent runtime operations between domain logic and channel adapters.

## Purpose

Coordinates the complete agent conversation lifecycle: receives inbound messages, executes agent turns (LLM calls + tool execution), persists state, and publishes events for observability.

## Key Capabilities

- **Message Routing** - Transform channel messages into agent runs
- **Turn Execution** - Orchestrate LLM completion streaming and tool call execution
- **Policy Enforcement** - Validate tool calls against configured policies
- **Session Management** - Trigger compaction when conversation exceeds token limits
- **Event Publishing** - Emit domain events for monitoring and debugging
- **Memory Injection** - Enrich prompts with relevant memories from storage

## Files

| File | Responsibility |
|------|---------------|
| `HandleInboundMessageUseCase.kt` | UC-001: Route inbound messages to agent runtime |
| `AgentTurnService.kt` | Core agent loop: LLM calls, tool execution, event publishing |
| `AgentRunService.kt` | Delegates to turn service |

## Domain Events

AgentTurnService publishes:
- `AgentLoopStarted/Completed` - run lifecycle
- `MessageAdded` - user/assistant message persisted
- `LlmCompletionRequested/Received` - LLM call lifecycle
- `ToolCallInitiated/Executed` - tool execution lifecycle
- `ResponseGenerated` - assistant response created

Pass `DomainEventPublisherPort` to enable; services work without it.
