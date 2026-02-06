# SDK

Core abstractions and contracts for plugin development.

## Purpose

Defines the interfaces that plugins must implement and the data structures used for communication between the core system and plugins. Provides the foundation for channel integrations.

## Key Capabilities

- **Channel Port Interface** - Contract for bidirectional messaging with external platforms
- **Message Types** - Standardized inbound/outbound message structures
- **Plugin Manifest** - Metadata for plugin discovery and loading
- **Serialization Support** - Kotlinx.serialization annotations for JSON handling

## Files

| File | Responsibility |
|------|---------------|
| `ChannelPort.kt` | Core interface for channel plugins with message handling |
| `PluginManifest.kt` | Plugin metadata descriptor for manifest loading |

## ChannelPort Contract

Implementations must:
1. Provide a unique `id` for the channel type
2. Handle long-running message listening in `start()`
3. Support graceful shutdown via `stop()`
4. Return results from `send()` operations

## Message Flow

```
Channel (external) → InboundMessage → Handler → Application
Application → OutboundMessage → Channel (external)
```

## Plugin Types

| Type | Description |
|------|-------------|
| `channel` | Messaging platform integration (Telegram, Slack, etc.) |
