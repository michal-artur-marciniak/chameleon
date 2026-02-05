# Chameleon

Bootstrap scaffold for the OpenClaw-inspired Chameleon platform.

## Modules

- `app` - application entrypoint, server
- `core` - domain layer (pure Kotlin)
- `infra` - infrastructure adapters (config loader, persistence, memory index)
- `sdk` - plugin SDK interfaces
- `plugins/telegram` - built-in Telegram channel plugin
- `extensions/` - external plugin drop-ins (created at runtime)

## Session Aggregate (DDD Core Domain)

Sessions manage conversation history and enforce compaction rules as a domain aggregate:

### Key Features

**Compaction Rules (Invariant-Enforced)**
- `shouldCompact()` - Determines when compaction is needed based on token/message thresholds
- `compact()` - Summarizes old messages while preserving the most recent user message
- `pruneToolResults()` - Removes bulky tool outputs while keeping transcript entries

**Domain Events**
- `ContextCompacted` - Emitted when session is compacted with summary info
- `MessageAdded` - Emitted when new message appended
- `ToolResultsPruned` - Emitted when tool outputs are cleaned up

**Invariants**
1. SessionKey is immutable and unique (set at creation)
2. Compaction never drops the most recent user message
3. Tool results can be pruned but transcript remains append-only
4. Summaries accumulate in the `summaries` list

**Key Files**
- `core/src/main/kotlin/agent/platform/session/Session.kt` - Domain aggregate
- `core/src/main/kotlin/agent/platform/session/SessionDomainEvents.kt` - Domain events
- `core/src/main/kotlin/agent/platform/session/CompactionConfig.kt` - Threshold configuration

## Agent Loop (DDD Core Domain)

Inbound channel messages run through a layered agent pipeline that keeps domain logic pure and pushes orchestration into the application layer.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer (app)                  │
│  HandleInboundMessageUseCase (UC-001)                       │
│  └── Maps InboundMessage → SessionKey                       │
│  └── Starts AgentRuntime                                    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer (infra)                │
│  DefaultAgentLoop (adapter)                                 │
│  └── Delegates to AgentTurnService                           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                Application Layer (core)                     │
│  AgentTurnService                                           │
│  ├── Session lock + persistence                             │
│  ├── LLM request building (LlmRequestBuilder)               │
│  ├── Tool execution + policy enforcement                    │
│  └── Domain event publishing                                │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (core)                      │
│  AgentLoop Aggregate                                        │
│  ├── create(agentId) - Factory method                       │
│  ├── processCompletion(events, deps)                        │
│  │   ├── Validates tool calls via ToolDefinitionRegistry    │
│  │   ├── Aggregates assistant output                        │
│  │   └── Emits TurnEvents + TurnPlan                         │
│  └── Enforces: tool-call validation, turn planning          │
└─────────────────────────────────────────────────────────────┘
```

### Key Files

**Domain Layer** (`core/src/main/kotlin/agent/platform/agent/domain/`):
- `AgentLoop.kt` - Core aggregate with `processCompletion()`
- `AgentLoopDomainEvents.kt` - Domain events (AgentLoopStarted, ToolExecuted, etc.)
- `core/src/main/kotlin/agent/platform/agent/ports/DomainEventPublisherPort.kt` - Port for publishing domain events

**Application Layer (core)** (`core/src/main/kotlin/agent/platform/application/`):
- `AgentRunService.kt` - Orchestrates run flow
- `AgentTurnService.kt` - Orchestrates turns, persistence, and tool execution
- `SessionAppService.kt` - Compaction orchestration
- `ToolExecutionService.kt` - Policy + execution facade
- `application/llm/LlmRequestBuilder.kt` - LLM request ACL for prompt construction
- `application/memory/MemoryContextAssembler.kt` - Builds memory context for prompts

**Infrastructure Layer** (`infra/src/main/kotlin/agent/platform/agent/`):
- `DefaultAgentLoop.kt` - Adapter delegating to AgentRunService
- `DefaultAgentRuntime.kt` - Runtime lifecycle management
- `LoggingDomainEventPublisher.kt` - Default domain event logging

**Contracts** (`core/src/main/kotlin/agent/platform/agent/`):
- `AgentContracts.kt` - Runtime and loop interfaces
- `AgentEvents.kt` - Event types (AssistantDelta, ToolEvent, etc.)
- `AgentModels.kt` - RunRequest, RunHandle, RunResult

### Domain Invariants (Enforced by AgentLoop Aggregate)

1. **Tool Validation**: Tool calls from the completion stream must exist in ToolDefinitionRegistry
2. **Turn Planning**: The turn produces a complete plan (assistant text + tool calls)
3. **Pure Domain Logic**: No persistence, external calls, or tool execution inside the aggregate

### Usage Pattern (Domain)

```kotlin
val agentLoop = AgentLoop.create(agentId)
val deps = AgentLoop.TurnDependencies(toolRegistry = toolDefinitionRegistry)

agentLoop.processCompletion(runId, llmProvider.stream(request), deps).collect { event ->
    when (event) {
        is TurnEvent.AssistantDelta -> handleAssistantText(event.text)
        is TurnEvent.TurnCompleted -> executePlan(event.plan)
    }
}
```

### Dependencies

- LLM routing uses provider/model refs resolved via `ModelRefResolver`
- Prompt assembly uses `LlmRequestBuilder` + `MemoryContextAssembler` in the core application layer
- Tool adapters provide definition lookup, policy checks, and execution
- Session persistence via `SessionFileRepository`

## Tool Policy Enforcement (DDD Core Domain)

Tool execution is governed by a **domain service** that enforces allow/deny/ask policies:

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (Core)                      │
│  ToolPolicyService                                          │
│  ├── evaluate(toolName, isExecTool, execCommand)            │
│  │   ├── Check deny list (takes precedence)                │
│  │   ├── Check allow list                                  │
│  │   ├── Handle exec tool security modes                   │
│  │   │   DENY: Always deny                                  │
│  │   │   ALLOWLIST: Check safe bins                         │
│  │   │   FULL: Allow all                                    │
│  │   └── Return Allow / Deny / Ask                         │
│  └── Safe bins validation for exec commands                │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                        │
│  ToolDefinitionRegistry / ToolPolicyEvaluator / Executor    │
│  ├── validatePolicy(call) → PolicyDecision                  │
│  ├── execute(call) with policy enforcement                  │
│  └── Emits ToolPolicyViolation domain events                │
└─────────────────────────────────────────────────────────────┘
```

### Policy Decision Types

- **`Allow`** - Tool execution permitted
- **`Deny`** - Tool execution blocked (returns ToolResult error)
- **`Ask`** - Approval required (returns ToolResult with approval request)

### Configuration

```kotlin
ToolsConfig(
    allow = listOf("read", "write", "edit", "exec"),  // Whitelist
    deny = listOf("dangerous_tool"),                   // Blacklist (precedence)
    exec = ExecToolConfig(
        security = ExecSecurity.ALLOWLIST,  // DENY | ALLOWLIST | FULL
        ask = AskMode.ON_MISS,              // OFF | ON_MISS | ALWAYS
        safeBins = listOf("jq", "grep", "cut", "sort", "uniq")
    )
)
```

### Key Files

- `core/src/main/kotlin/agent/platform/tool/ToolPolicyService.kt` - Domain service
- `core/src/main/kotlin/agent/platform/tool/ToolDomainEvents.kt` - Domain events
- `core/src/main/kotlin/agent/platform/tool/ToolRegistry.kt` - Ports (definition, policy, execution)
- `infra/src/main/kotlin/agent/platform/tool/InMemoryToolDefinitionRegistry.kt` - Definitions adapter
- `infra/src/main/kotlin/agent/platform/tool/ToolPolicyEvaluatorAdapter.kt` - Policy adapter
- `infra/src/main/kotlin/agent/platform/tool/ToolExecutorAdapter.kt` - Executor adapter

### Invariants

1. **Deny takes precedence** - Tools in both lists are denied
2. **Unknown tools require approval** - Follows ask mode configuration
3. **Exec tool special handling** - Additional security layers beyond basic policy
4. **Domain events emitted** - `ToolPolicyViolation` events for deny/ask decisions

## Memory Index (DDD Supporting Domain)

Long-term memory is managed through a **supporting domain aggregate** that provides hybrid search capabilities over indexed files:

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (Core)                      │
│  MemoryIndex Aggregate                                      │
│  ├── create(config, repository) - Factory method            │
│  ├── indexFile(file)                                        │
│  │   ├── Chunks file with configurable overlap              │
│  │   ├── Generates content-addressed chunk IDs              │
│  │   └── Emits FileIndexed domain event                     │
│  ├── search(query)                                          │
│  │   ├── Delegates to repository                            │
│  │   └── Emits MemorySearchPerformed event                  │
│  └── clear() / removeFile()                                 │
└─────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                        │
│  SqliteMemoryIndexAdapter                                   │
│  ├── Implements MemoryIndexRepositoryPort (ports/)          │
│  ├── SQLite FTS5 virtual table for full-text search         │
│  ├── Triggers keep FTS index synchronized                   │
│  └── Content-addressed chunk storage                        │
└─────────────────────────────────────────────────────────────┘
```

### Key Features

**Content-Addressed Chunking**
- Files are split into chunks with configurable size and overlap
- Chunk IDs generated from content hash (idempotent indexing)
- Supports: Markdown, code files, configs (configurable extensions)

**Full-Text Search (FTS5)**
- SQLite FTS5 virtual table for efficient text search
- Prefix matching support (`term*`)
- Rank-based relevance scoring
- File path filtering

**Domain Events**
- `FileIndexed` - Emitted when a file is indexed
- `MemorySearchPerformed` - Emitted when search executes
- `IndexCleared` - Emitted when index is cleared
- `FileRemovedFromIndex` - Emitted when file is removed

### Configuration

```kotlin
MemoryConfig(
    chunkSizeLines = 50,          // Lines per chunk
    chunkOverlapLines = 5,        // Overlap between chunks
    maxChunksPerFile = 1000,      // Safety limit per file
    defaultMaxSearchResults = 10, // Default result limit
    minSearchRelevanceScore = 0.0 // Minimum relevance threshold
)
```

### Key Files

**Domain Layer** (`core/src/main/kotlin/agent/platform/memory/`):
- `MemoryIndex.kt` - Core aggregate with indexing logic
- `MemoryChunk.kt` - Value objects (chunks, search results, queries)
- `MemorySearchService.kt` - Domain service for search operations
- `MemoryDomainEvents.kt` - Domain events
- `core/src/main/kotlin/agent/platform/memory/ports/MemoryIndexRepositoryPort.kt` - Repository port interface

**Application Layer (core)**:
- `core/src/main/kotlin/agent/platform/application/memory/MemoryContextAssembler.kt` - Builds memory context for prompts

**Infrastructure Layer** (`infra/src/main/kotlin/agent/platform/memory/`):
- `SqliteMemoryIndexAdapter.kt` - SQLite FTS5 implementation

### Invariants

1. **Chunk IDs are content-addressed** - Same content = same ID (idempotent)
2. **Index is rebuildable from source files** - Chunks can be recreated
3. **FTS index stays synchronized** - Database triggers maintain consistency
4. **Files are chunked with overlap** - Configurable overlap prevents context loss

### Integration with Agent Loop

Memory search is composed in the application layer. The domain aggregate stays pure; any memory context is injected when building the LLM request in the application service.

## Application Layer (DDD Use Cases)

The application layer implements use cases that orchestrate domain objects:

Located in `app/src/main/kotlin/agent/platform/application/`:

- **`HandleInboundMessageUseCase`** (UC-001) - Routes channel messages through DDD agent runtime
  - Maps `InboundMessage` to `SessionKey`
  - Starts agent runs via `AgentRuntime`
  - Streams lifecycle events (START, END, ERROR)

**Domain Events** (in `core/src/main/kotlin/agent/platform/agent/domain/`):
- `AgentLoopStarted` - Run initiated
- `ToolCallInitiated` - Tool execution started
- `ToolExecuted` - Tool execution completed
- `ResponseGenerated` - Assistant response created
- `AgentLoopCompleted` - Run finished (success/error)

**Usage Pattern**:
```kotlin
// In PluginService or channel adapters
val useCase = HandleInboundMessageUseCase(agentRuntime)
useCase.executeAndRespond(channel, inboundMessage, agentId)
```

Channel adapters remain thin - all orchestration lives in the application layer.

## Build

```bash
./gradlew build
```

## Configuration (OpenClaw-Compatible)

Configuration loads from:
1. `config/config.json`
2. `app/src/main/resources/config.default.json`

Environment variables are expanded with `${VAR_NAME}` syntax. Missing variables cause startup to fail.

### Minimal Example

```json
{
  "models": {
    "providers": {
      "kimi": {
        "baseUrl": "https://api.moonshot.cn/v1",
        "apiKey": "${KIMI_API_KEY}",
        "models": [
          {
            "id": "kimi-k2.5",
            "name": "Kimi K2.5",
            "contextWindow": 256000,
            "maxTokens": 8192,
            "reasoning": true
          }
        ]
      }
    }
  },
  "agents": {
    "defaults": {
      "model": { "primary": "kimi/kimi-k2.5" },
      "workspace": "/app/workspace",
      "extensionsDir": "/app/extensions",
      "contextTokens": 128000,
      "timeoutSeconds": 600,
      "thinkingDefault": "off",
      "verboseDefault": "off",
      "compaction": {
        "reserveTokensFloor": 20000,
        "softThresholdTokens": 4000,
        "memoryFlush": { "enabled": true }
      }
    },
    "list": [{ "id": "main", "default": true }]
  },
  "messages": {
    "groupChat": { "historyLimit": 50 },
    "dm": { "historyLimit": 100 },
    "queue": { "mode": "sequential", "cap": 10, "debounceMs": 100, "maxConcurrent": 1 }
  },
  "tools": {
    "exec": {
      "security": "allowlist",
      "ask": "on-miss",
      "safeBins": ["jq", "grep", "cut", "sort", "uniq", "head", "tail", "tr", "wc"]
    },
    "allow": ["read", "write", "edit", "exec", "memory_search", "memory_get"]
  },
  "channels": {
    "telegram": {
      "enabled": true,
      "token": "${TELEGRAM_TOKEN}",
      "mode": "polling",
      "requireMention": true,
      "allowedUsers": [],
      "allowedGroups": []
    }
  },
  "gateway": { "host": "0.0.0.0", "port": 18789 }
}
```

### Key Config Sections

- **models.providers**: LLM providers, API keys, model catalog
- **agents.defaults**: global defaults (workspace, compaction, thinking/verbose)
- **messages**: history limits + queue mode
- **tools.exec**: exec security & allowlist
- **channels.telegram**: token, mention requirements, allowlists

## Plugin System Architecture

Our plugin system is a **domain-driven, channel-based plugin model** designed for extensibility and type safety. It follows DDD principles with clear separation between official (built-in) and external plugins.

### Four-Layer Architecture

#### 1. SDK Layer (Contracts)
Located in `sdk/` module - defines interfaces and data structures all plugins must implement:

- **`ChannelPort`** - Core interface for messaging channels
  - `start(handler: suspend (InboundMessage) -> Unit)` - Begin listening for inbound messages
  - `send(message: OutboundMessage): Result<Unit>` - Send outbound messages
  - `stop()` - Cleanup and shutdown

- **`PluginManifest`** - Plugin metadata (id, version, entryPoint, type)

- **Message Data Classes** - Standardized message format
  - `InboundMessage` - channelId, chatId, userId, text, isGroup, isMentioned
  - `OutboundMessage` - channelId, chatId, text

#### 2. Domain Layer (Plugin Context)
Located in `core/src/main/kotlin/agent/platform/plugins/domain/` - DDD aggregate root and value objects:

- **`PluginManager`** - Aggregate root for plugin lifecycle
  - `loadAll()` - Discovers and loads all plugins (official + external)
  - `enable(id)`, `disable(id)` - Lifecycle management
  - `reload(id)` - Hot-reload for external plugins
  - Emits domain events: `PluginLoaded`, `PluginEnabled`, `PluginDisabled`, `PluginError`

- **`Plugin`** - Entity representing a loaded plugin
  - `id: PluginId`, `version: PluginVersion` - Value objects
  - `type: PluginType` - CHANNEL | LLM | TOOL
  - `source: PluginSource` - OFFICIAL | EXTERNAL
  - `status: PluginStatus` - DISCOVERED | LOADED | ENABLED | DISABLED

- **Value Objects**
  - `PluginId` - Validated unique identifier (lowercase, alphanumeric, hyphens)
  - `PluginVersion` - SemVer parsing (major.minor.patch)
  - `PluginCapability` - RECEIVE_MESSAGES | SEND_MESSAGES | PROVIDE_LLM | EXECUTE_TOOL

- **Repositories**
  - `OfficialPluginRegistry` - Factory registry for built-in plugins
  - `FileSystemPluginRepository` - JAR loading via URLClassLoader for external plugins

#### 3. Plugin Implementation Layer
Located in `plugins/` directory - concrete channel implementations:

**Official Plugins** (compiled with application):
- **Telegram Plugin** (`plugins/telegram/`)
  - Implements `ChannelPort` interface
  - Registered in `OfficialPluginRegistry` via `PluginFactory`
  - Uses Ktor HTTP client for Telegram Bot API
  - Features: long-polling, webhook cleanup, @mention detection, mention gating

**External Plugins** (loaded from filesystem):
- Drop JAR + `plugin.json` into `extensions/<name>/`
- Auto-discovered and loaded via `FileSystemPluginRepository`
- Constructor injection: `(PluginManifest)`, `(PlatformConfig)`, `(JsonObject)`, or no-args
- Official plugins take precedence (external skipped if ID conflicts)

#### 4. Application Layer (Orchestration)
Located in `app/` - uses domain layer:

- **`PluginService`** - Application service
  - Creates `PluginManager` aggregate root
  - Subscribes to domain events for logging
  - Starts enabled plugins in coroutine scopes
  - Provides `listPlugins()`, `enablePlugin()`, `disablePlugin()`, `reloadPlugin()`

### Runtime Flow

```
1. Application startup
        ↓
2. PluginManager.loadAll()
   ├─ OfficialPluginRegistry.discover() → built-in factories
   └─ FileSystemPluginRepository.discover() → external manifests
        ↓
3. Load plugins
   ├─ Official: factory.create(config) → Plugin entity
   └─ External: URLClassLoader.loadClass() → Plugin entity
        ↓
4. Emit domain events
   PluginLoaded, PluginEnabled, etc.
        ↓
5. PluginService starts enabled plugins
   plugin.start { inbound → handleMessage() }
```

### Key Design Principles

**Domain-Driven Design**
- `PluginManager` as aggregate root with clear boundaries
- Value objects enforce invariants (PluginId validation, SemVer)
- Domain events for loose coupling (logging, metrics)
- Repository pattern for different plugin sources

**Separation of Concerns**
- Core application knows nothing about Telegram specifics
- Domain layer handles plugin lifecycle rules
- Infrastructure (JAR loading) isolated in repositories
- Message format standardized across all channels

**Type Safety**
- Kotlin interfaces ensure compile-time correctness
- Value objects prevent invalid states
- Sealed class hierarchy for domain events
- Clear contracts between layers

**Extensibility**
- Official plugins: register in `OfficialPluginRegistry`
- External plugins: drop JAR in `extensions/<name>/`
- New channels (WhatsApp, Discord) implement `ChannelPort`
- Hot-reload for external plugin development

**Plugin Loading Strategy**
1. Official plugins load first (ensures core functionality)
2. External plugins load second
3. External plugins skipped if ID conflicts with official
4. All plugins registered in `PluginManager` aggregate

### Current Capabilities

- ✅ **Official + External**: Built-in plugins + dynamic JAR loading
- ✅ **Channels**: `ChannelPort` interface with Telegram implementation
- ✅ **Hot-reload**: External plugins can be reloaded at runtime
- ✅ **Type-safe**: DDD value objects (PluginId, PluginVersion)
- ✅ **Domain events**: Plugin lifecycle events for observability

### Future Roadmap

**M4+ Enhancements**:
- `ToolPort` interface for custom tool plugins
- LLM provider plugins (OpenAI, Claude, etc.)
- Webhook-based channel support
- Plugin configuration schema validation
- Plugin dependency resolution

### Adding a New Channel Plugin

#### Option A: Official Plugin (Built-in)

Add to the monorepo for core team maintenance:

1. Create `plugins/discord/` module
2. Implement `ChannelPort` interface
3. Register in `OfficialPluginRegistry`:
   ```kotlin
   register(discordFactory())
   ```
4. Add to `settings.gradle.kts` include list
5. Configure in `config/config.json`

The plugin will be compiled with the application and loaded automatically.

#### Option B: External Plugin (JAR)

For third-party or custom plugins:

1. Create a new Kotlin project
2. Implement `ChannelPort` interface
3. Create `plugin.json`:
   ```json
   {
     "id": "my-custom",
     "version": "1.0.0",
     "entryPoint": "com.example.MyPlugin",
     "type": "channel"
   }
   ```
4. Build fat JAR with dependencies
5. Deploy to `extensions/my-custom/plugin.jar`
6. Deploy manifest to `extensions/my-custom/plugin.json`

The `PluginManager` will auto-discover and load on startup. Official plugins take precedence if ID conflicts.

## Running

### Local Development

```bash
# Setup environment
cp .env.example .env
# Edit .env with your TELEGRAM_TOKEN

# Run application (uses default config from resources)
./gradlew :app:run

# Test health endpoint
curl http://localhost:18789/health

# Test WebSocket echo
websocat ws://localhost:18789/ws
```

### Docker

```bash
# Build fat JAR
./gradlew :app:fatJar

# Create directories for runtime data
mkdir -p workspace extensions data

# Copy and customize config (optional - defaults work out of the box)
mkdir -p config
cp app/src/main/resources/config.default.json config/config.json
# Edit config/config.json to customize

# Start with Docker Compose
docker compose up -d
```

### Configuration

Configuration is loaded in order of priority:
1. `--config=/path/to/config.json` (CLI override)
2. `config/config.json` (if exists in working directory)
3. `app/src/main/resources/config.default.json` (bundled fallback)

Environment variables are expanded using `${VAR_NAME}` syntax in config files. Missing variables cause startup to fail.

### Logging

Precedence: **CLI args > ENV > config.json > defaults**

**Config keys:**
- `logging.level`: `trace|debug|info|warn|error`
- `logging.format`: `plain|json`
- `logging.debug`: `true|false` (forces `debug` + stacktrace)
- `logging.stacktrace`: `true|false`

**Env overrides:**
- `LOG_LEVEL`, `LOG_FORMAT`, `LOG_DEBUG`, `LOG_STACKTRACE`

**CLI overrides:**
```
--config=/path/to/config.json
--log-level=debug
--log-format=json
--log-debug=true
--log-stacktrace=true
```

**Note:** Unhandled JVM exceptions may still print stack traces to stderr even when `stacktrace=false`.

### Session Storage

Sessions follow OpenClaw-style persistence:

```
<workspace>/sessions/
├── sessions.json          # index (metadata)
└── <sessionId>.jsonl      # message transcript
```

Session keys look like:

- DM: `agent:main:telegram:dm:123456`
- Group: `agent:main:telegram:group:-1001234567890`

Runtime directories are created automatically on startup:
- `workspace/` - Session files and user data
- `extensions/` - External plugin JARs
- `data/` - SQLite indices and runtime data

## License

MIT
