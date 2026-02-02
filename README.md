# Chameleon

Bootstrap scaffold for the OpenClaw-inspired Chameleon platform.

## Modules

- `app` - application entrypoint, DI, config (loader + default), server
- `core` - domain layer (pure Kotlin)
- `infra` - infrastructure adapters
- `sdk` - plugin SDK interfaces
- `plugins/telegram` - built-in Telegram channel plugin
- `extensions/` - external plugin drop-ins (created at runtime)

## Build

```bash
./gradlew build
```

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
  - Features: long-polling, webhook cleanup, @mention detection

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
1. `config/config.json` (if exists in working directory)
2. `app/src/main/resources/config.default.json` (bundled fallback)

Environment variables are expanded using `${VAR_NAME}` syntax in config files.

Runtime directories are created automatically on startup:
- `workspace/` - Session files and user data
- `extensions/` - External plugin JARs
- `data/` - SQLite indices and runtime data

## License

MIT
