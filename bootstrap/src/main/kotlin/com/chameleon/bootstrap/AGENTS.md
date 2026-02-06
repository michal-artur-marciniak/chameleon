# Bootstrap Layer

Application bootstrap and runtime initialization for the Chameleon platform.

## Purpose

Responsible for starting up the entire application: loading configuration, initializing logging, starting the HTTP gateway server, and orchestrating plugin lifecycle.

## Key Capabilities

- **Configuration Loading** - Load and merge config from files and environment
- **Logging Setup** - Configure logback based on platform configuration
- **HTTP Gateway** - Start Ktor server with health checks and WebSocket support
- **Plugin Orchestration** - Load, start, and manage all channel plugins
- **Startup Logging** - Log configuration summary and diagnostic information
- **Application Entry Point** - Main function that wires everything together

## Files

| File | Responsibility |
|------|---------------|
| `Application.kt` | Main entry point: orchestrates bootstrap sequence |
| `StartupLogger.kt` | Logs startup configuration and diagnostic info |
| `ServerFactory.kt` | Creates Ktor HTTP server with routing |
| `PluginService.kt` | Orchestrates plugin loading and lifecycle management |

## Bootstrap Sequence

1. **Load Configuration** - `ConfigLoader` reads config files and env vars
2. **Configure Logging** - `LoggingConfigurator` applies log settings
3. **Log Startup Info** - `StartupLogger` outputs config summary
4. **Start HTTP Server** - `ServerFactory` creates gateway on configured port
5. **Start Plugins** - `PluginService` loads and starts all enabled plugins
6. **Block & Wait** - Server runs until termination signal

## Plugin Lifecycle

PluginService manages plugins through the domain:
- **Discovery** - Scan official registry and external directory
- **Loading** - Instantiate plugin via factory
- **Starting** - Launch plugin coroutine with exception handling
- **Running** - Route inbound messages to agent runtime (UC-001)
- **Stopping** - Cancel coroutine scopes on shutdown

## Dependencies

- **Config** - `PlatformConfig` for shared configuration values
- **Application** - `HandleInboundMessageUseCase` for message routing
- **Domain** - `PluginManager` aggregate for plugin lifecycle
- **Infra** - `AgentRuntimeFactory`, `ConfigLoader`, `LoggingConfigurator`
