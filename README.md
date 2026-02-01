# Agent Platform

Bootstrap scaffold for the OpenClaw-inspired agent platform.

## Modules

- `app` - application entrypoint, DI, config, server
- `core` - domain layer (pure Kotlin)
- `infra` - infrastructure adapters
- `sdk` - plugin SDK interfaces
- `plugins/telegram` - built-in Telegram channel plugin

## Build

```bash
./gradlew build
```

## Plugin System Architecture

Our plugin system is a **channel-based plugin model** designed for extensibility and type safety. It separates messaging channel implementations from the core application logic.

### Three-Layer Architecture

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

#### 2. Plugin Implementation Layer
Located in `plugins/` directory - concrete channel implementations:

**Example: Telegram Plugin** (`plugins/telegram/`)
- Implements `ChannelPort` interface
- Uses Ktor HTTP client for Telegram Bot API communication
- Features:
  - Long-polling message retrieval
  - Automatic webhook cleanup on startup
  - @mention detection in group chats
  - Configurable group mention requirements
- Self-contained with dedicated `build.gradle.kts`

#### 3. Application Layer (Orchestration)
Located in `app/` - loads and wires plugins:

- **`PluginLoader`** - Discovers plugins by scanning `plugins/` directory
  - Reads `plugin.json` manifests
  - Currently supports built-in plugins (compiled with app)

- **Main Application** (`Application.kt`)
  - Instantiates and configures plugins
  - Starts plugins in coroutine scopes
  - Connects plugins to session management and message handlers

### Runtime Flow

```
1. Application startup
        ↓
2. PluginLoader scans plugins/ directory
        ↓
3. Parse plugin.json manifests
   Instantiate plugin classes
        ↓
4. Configure plugins with credentials/settings
   (e.g., Telegram token from config)
        ↓
5. Call plugin.start() with message handler
        ↓
6. Handler processes InboundMessage:
   - Update session index metadata
   - Echo response via plugin.send()
```

### Key Design Principles

**Separation of Concerns**
- Core application knows nothing about Telegram specifics
- Plugins handle all channel-specific logic
- Message format is standardized across all channels

**Type Safety**
- Kotlin interfaces ensure compile-time correctness
- No reflection or dynamic loading in MVP
- Clear contracts between layers

**Testability**
- Mock ChannelPort implementations for testing
- Isolated plugin units can be tested independently
- No dependencies on external APIs in core logic

**Extensibility**
- New channels (WhatsApp, Discord, Slack) just implement `ChannelPort`
- No changes required to core application
- Consistent message handling across all channels

### Current Limitations (M1/M2 MVP)

- **Built-in only**: Plugins compiled with application, no external JAR loading
- **Channels only**: Only `ChannelPort` interface; tool plugins not yet implemented
- **Static loading**: No hot-reload or dynamic plugin discovery

### Future Roadmap

**M4+ Enhancements**:
- External plugin loading from `plugins/*.jar`
- Hot-reload capability for development
- `ToolPort` interface for custom tool plugins
- LLM provider plugins (OpenAI, Claude, etc.)
- Webhook-based channel support

### Adding a New Channel Plugin

To add a new messaging channel (e.g., Discord):

1. Create `plugins/discord/` module
2. Implement `ChannelPort` interface
3. Add `plugin.json` manifest:
   ```json
   {
     "id": "discord",
     "version": "0.1.0",
     "entryPoint": "agent.plugin.discord.DiscordPlugin",
     "type": "channel"
   }
   ```
4. Implement polling/webhook logic in `DiscordPlugin.kt`
5. Add to `settings.gradle.kts` include list
6. Configure in `config/config.json`

The application will automatically load and start the plugin on next run.

## Running

### Local Development

```bash
# Setup environment
cp .env.example .env
# Edit .env with your TELEGRAM_TOKEN

# Run application
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

# Start with Docker Compose
docker compose -f docker/docker-compose.yml up -d
```

## License

MIT
