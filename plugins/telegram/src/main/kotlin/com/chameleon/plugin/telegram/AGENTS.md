# Telegram Plugin

Telegram Bot API channel integration for bidirectional messaging.

## Purpose

Provides a [ChannelPort](../../../../../sdk/src/main/kotlin/com/chameleon/sdk/ChannelPort.kt) implementation that connects the application to Telegram via the Bot API. Supports both private chats and group conversations with mention-based filtering.

## Key Capabilities

- **Long-Polling** - Receives messages via getUpdates with 60-second timeout
- **Mention Filtering** - Optionally requires @botname mention in group chats
- **Webhook Cleanup** - Automatically removes webhooks on startup for polling mode
- **Error Handling** - Gracefully handles API errors and network timeouts
- **HTTP Client** - Ktor-based client with CIO engine and JSON serialization

## Files

| File | Responsibility |
|------|---------------|
| `TelegramPlugin.kt` | ChannelPort implementation with polling loop |
| `TelegramModels.kt` | DTOs for Telegram Bot API requests/responses |
| `plugin.json` | Plugin manifest for discovery |

## Configuration

```kotlin
val plugin = TelegramPlugin(
    token = "YOUR_BOT_TOKEN",
    requireMentionInGroups = true  // Only respond to @mentions in groups
)
```

## Message Flow

```
Telegram Server → getUpdates → TelegramUpdate → InboundMessage → Handler
Handler → OutboundMessage → sendMessage → Telegram Server
```

## API Coverage

| Endpoint | Purpose |
|----------|---------|
| `getUpdates` | Receive incoming messages (long-polling) |
| `sendMessage` | Send replies to chats |
| `getMe` | Fetch bot info (username for mentions) |
| `deleteWebhook` | Remove webhook for polling mode |

## Group Chat Behavior

When `requireMentionInGroups` is enabled:
- Private chats: All messages processed
- Group chats: Only messages containing `@botname` are processed
- Bot username is fetched from `getMe` on startup

## Error Handling

- API errors logged with error code and description
- Network timeouts handled via `withTimeoutOrNull`
- Non-fatal errors don't stop the polling loop
