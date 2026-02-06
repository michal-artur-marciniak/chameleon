# Session Module Conventions

## Session Aggregate Pattern

The Session aggregate lives in `Session.kt` and owns conversation history and compaction rules.

### Key Patterns

**1. Domain Aggregate with Factory**
```kotlin
// Sessions are created via SessionManager
val session = sessionManager.loadOrCreate(sessionKey)

// Or created directly
val session = Session(
    id = SessionId.generate(),
    key = sessionKey,
    messages = emptyList(),
    config = CompactionConfig()
)
```

**2. Immutable Updates with Domain Events**
All modifications return both the new state and the domain event:
```kotlin
val (newSession, messageAddedEvent) = session.withMessage(message)
val (newSession, prunedEvent) = session.pruneToolResults()
val compactionResult = session.compact(maxMessagesToKeep = 50)
```

**3. Compaction Behavior**
- The `compact()` method summarizes old messages and optionally prunes tool results
- Always preserves the most recent user message (invariant)
- Adds a system message with the summary when compacting
- Emits `ContextCompacted` domain event
- Tool results can be pruned without removing transcript entries

**4. Token Estimation**
Rough estimation: ~4 characters per token + overhead:
```kotlin
val tokenCount = session.estimateTokens()
if (session.shouldCompact(tokenCount, maxTokens)) {
    val result = session.compact()
    // Use result.newSession and publish result.event
}
```

## Invariants

1. **SessionKey is immutable and unique** - Set at creation
2. **Compaction never drops the most recent user message** - Enforced in `compact()`
3. **Tool results can be pruned but transcript remains append-only** - Tool messages stay but content is replaced with placeholders
4. **Summaries accumulate** - Each compaction adds to the `summaries` list

## File Organization

- `Session.kt` - Domain aggregate with compaction logic
- `SessionDomainEvents.kt` - Domain events (ContextCompacted, MessageAdded, ToolResultsPruned)
- `CompactionConfig.kt` - Configuration for thresholds and behavior
- `Message.kt` - Message model with roles (SYSTEM, USER, ASSISTANT, TOOL)
- `SessionManager.kt` - Port for session lifecycle management
- `SessionRepository.kt` - Port for persistence

## When Modifying Session

1. Always return domain events for state changes
2. Preserve invariants in `compact()` - especially the most recent user message rule
3. Update `CompactionConfig` if adding new configuration options
4. Keep `estimateTokens()` in sync with actual token counting
5. Test compaction scenarios in `SessionTest.kt`

## Testing Compaction

Key test scenarios:
- Compaction preserves recent messages
- Most recent user message is never dropped
- Tool results are pruned when enabled
- ContextCompacted event is emitted with correct counts
- Empty sessions handle gracefully
