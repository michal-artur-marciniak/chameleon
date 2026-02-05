# Chameleon

Bootstrap scaffold for the OpenClaw-inspired Chameleon platform.

## Modules

- `app` - application entrypoint, server
- `core` - domain layer (pure Kotlin)
- `infra` - infrastructure adapters (config loader, persistence)
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

Inbound channel messages run through a **domain-driven agent loop** with clear separation between domain logic and infrastructure:

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  HandleInboundMessageUseCase (UC-001)                       │
│  └── Maps InboundMessage → SessionKey                       │
│  └── Starts AgentRuntime → Delegates to AgentLoop aggregate │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (Core)                      │
│  AgentLoop Aggregate                                        │
│  ├── create(agentId) - Factory method                       │
│  ├── runTurn(session, message, deps)                        │
│  │   ├── Adds user message to session                       │
│  │   ├── Streams LLM completion                             │
│  │   ├── Validates tool calls via ToolRegistry              │
│  │   ├── Executes tools and persists results                │
│  │   └── Emits domain events                                │
│  └── Enforces: Tool validation, persistence, atomic turns   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                        │
│  DefaultAgentLoop (adapter)                                 │
│  ├── Resolves provider/model refs                           │
│  ├── Wires concrete repositories to domain ports            │
│  └── Maps TurnEvents → AgentEvents                          │
└─────────────────────────────────────────────────────────────┘
```

### Key Files

**Domain Layer** (`core/src/main/kotlin/agent/platform/agent/domain/`):
- `AgentLoop.kt` - Core aggregate with `runTurn()` method
- `AgentLoopDomainEvents.kt` - Domain events (AgentLoopStarted, ToolExecuted, etc.)
- `DomainEventPublisherPort.kt` - Port for publishing domain events

**Infrastructure Layer** (`infra/src/main/kotlin/agent/platform/agent/`):
- `DefaultAgentLoop.kt` - Adapter that delegates to domain aggregate
- `DefaultAgentRuntime.kt` - Runtime lifecycle management
- `LoggingDomainEventPublisher.kt` - Default domain event logging

**Contracts** (`core/src/main/kotlin/agent/platform/agent/`):
- `AgentContracts.kt` - Runtime and loop interfaces
- `AgentEvents.kt` - Event types (AssistantDelta, ToolEvent, etc.)
- `AgentModels.kt` - RunRequest, RunHandle, RunResult

### Domain Invariants (Enforced by AgentLoop Aggregate)

1. **Tool Validation**: All tool calls are validated through ToolRegistry before execution
2. **Persistence**: Assistant responses are persisted to SessionRepository atomically
3. **Atomic Turns**: Each turn is executed atomically per session
4. **Domain Events**: Significant events are published via DomainEventPublisherPort

### Usage Pattern

```kotlin
// Create the domain aggregate
val agentLoop = AgentLoop.create(agentId)

// Prepare dependencies (passed in for purity)
val deps = AgentLoop.TurnDependencies(
    sessionRepository = sessionRepo,
    toolRegistry = toolRegistry,
    llmProvider = llmProvider,
    contextBundle = context,
    eventPublisher = eventPublisher  // optional
)

// Execute a turn
agentLoop.runTurn(runId, session, userMessage, deps).collect { event ->
    when (event) {
        is TurnEvent.AssistantDelta -> handleAssistantText(event.text)
        is TurnEvent.ToolStarted -> showToolIndicator(event.toolName)
        is TurnEvent.ToolCompleted -> displayToolResult(event.result)
    }
}
```

### Dependencies

- LLM routing uses provider/model refs (OpenAI-compatible providers configured under `models.providers`), resolved via the core `ModelRefResolver`
- Tool registry (`InMemoryToolRegistry`) validates and executes tool calls
- Session persistence via `SessionFileRepository`

## Application Layer (DDD Use Cases)

The application layer implements use cases that orchestrate domain objects:

Located in `app/src/main/kotlin/agent/platform/application/`:

- **`HandleInboundMessageUseCase`** (UC-001) - Routes channel messages through DDD agent runtime
  - Maps `InboundMessage` to `SessionKey` 
  - Starts agent runs via `AgentRuntime`
  - Streams lifecycle events (START, END, ERROR)
  - Publishes domain events via `DomainEventPublisherPort`

**Domain Events** (in `core/src/main/kotlin/agent/platform/agent/domain/`):
- `AgentLoopStarted` - Run initiated
- `ToolCallInitiated` - Tool execution started
- `ToolExecuted` - Tool execution completed
- `ResponseGenerated` - Assistant response created
- `AgentLoopCompleted` - Run finished (success/error)

**Usage Pattern**:
```kotlin
// In PluginService or channel adapters
val useCase = HandleInboundMessageUseCase(agentRuntime, eventPublisher)
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
