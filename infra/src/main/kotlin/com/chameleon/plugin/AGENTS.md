# Plugins

Channel integration plugins for external messaging platforms.

## Purpose

Plugins provide concrete implementations of the [ChannelPort](../../../sdk/src/main/kotlin/com/chameleon/sdk/ChannelPort.kt) interface, enabling the application to communicate with various messaging platforms. Each plugin is loaded dynamically based on its `plugin.json` manifest.

## Plugin Structure

Each plugin resides in its own subdirectory under `plugins/` with this structure:

```
plugins/{name}/
├── src/main/kotlin/com/chameleon/plugin/{name}/
│   ├── {Name}Plugin.kt       # ChannelPort implementation
│   └── {Name}Models.kt       # Platform-specific DTOs
├── build.gradle.kts          # Plugin-specific dependencies
└── plugin.json               # Plugin manifest
```

## Plugin Manifest

Every plugin requires a `plugin.json` file:

```json
{
  "id": "telegram",
  "version": "0.1.0",
  "entryPoint": "com.chameleon.plugin.telegram.TelegramPlugin",
  "type": "channel"
}
```

| Field | Description |
|-------|-------------|
| `id` | Unique plugin identifier |
| `version` | Semantic version |
| `entryPoint` | Fully qualified class implementing ChannelPort |
| `type` | Plugin category: `"channel"` for messaging integrations |

## Available Plugins

| Plugin | Platform | Key Features |
|--------|----------|--------------|
| `telegram` | Telegram Bot API | Long-polling, mention filtering, group/private chats |

## Creating a New Plugin

1. Create directory: `plugins/{name}/`
2. Implement `ChannelPort` interface
3. Create `plugin.json` manifest
4. Add plugin-specific dependencies in `build.gradle.kts`

## Implementation Guidelines

- **ID uniqueness**: Plugin IDs must be unique across all plugins
- **Error handling**: Non-fatal errors should not stop the polling loop
- **Resource cleanup**: Implement `stop()` to release resources (HTTP clients, connections)
- **Configuration**: Accept configuration via constructor parameters
- **Message transformation**: Convert platform messages to/from `InboundMessage`/`OutboundMessage`
