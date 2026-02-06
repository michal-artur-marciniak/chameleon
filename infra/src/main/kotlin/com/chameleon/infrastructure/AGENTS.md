# Infrastructure Layer

Provides concrete implementations of domain and application layer ports.

## Purpose

Implements all external concerns: LLM providers, persistence, configuration, logging, and plugin loading. Adapts external libraries and services to the clean architecture ports defined by inner layers.

## Key Capabilities

- **LLM Integration** - OpenAI-compatible provider with streaming support
- **Configuration Loading** - JSON config with env var expansion and .env support
- **Session Persistence** - File-based storage with JSONL message logs
- **Memory Indexing** - SQLite FTS5 for full-text search over memory chunks
- **Plugin System** - FileSystem and built-in plugin registries with dynamic loading
- **Tool Execution** - Built-in tool handlers with policy validation
- **Logging** - SLF4J wrapper with runtime stacktrace toggle
- **Agent Runtime** - Coroutine-based runtime with event streaming

## Files

| File | Responsibility |
|------|---------------|
| **agent/** | Agent runtime implementations |
| `AgentRuntimeFactory.kt` | Factory wiring all infrastructure dependencies |
| `DefaultAgentRuntime.kt` | Coroutine-based runtime managing agent runs |
| `DefaultAgentLoop.kt` | Delegates to application service with error handling |
| `DefaultContextAssembler.kt` | Builds prompts with bootstrap file injection |
| `LoggingDomainEventPublisher.kt` | Logs domain events via SLF4J |
| **config/** | Configuration loading and overrides |
| `ConfigLoader.kt` | Loads JSON config with env expansion and logging overrides |
| `ConfigPath.kt` | Config file path resolution strategies |
| `LogOverrides.kt` | Logging override merging from multiple sources |
| **llm/** | LLM provider implementations |
| `OpenAiCompatProvider.kt` | OpenAI-compatible API client via Ktor |
| `ProviderRegistry.kt` | In-memory registry of LLM providers |
| **logging/** | Logging utilities |
| `LogWrapper.kt` | SLF4J wrapper with stacktrace toggle |
| `LoggingConfigurator.kt` | Applies logging config to system properties |
| **memory/** | Memory storage adapters |
| `SqliteMemoryIndexAdapter.kt` | SQLite FTS5-backed memory index |
| **persistence/** | Session persistence |
| `SessionFileRepository.kt` | File-based session storage (JSONL + index) |
| `SessionIndexStore.kt` | Session index entry data class |
| **plugins/** | Plugin loading |
| `FileSystemPluginRepository.kt` | Loads external plugins from filesystem |
| `OfficialPluginRegistry.kt` | Built-in plugin registry (Telegram) |
| **session/** | Session management |
| `DefaultSessionManager.kt` | Per-session locking and lifecycle |
| **tool/** | Tool execution |
| `InMemoryToolDefinitionRegistry.kt` | In-memory tool registry |
| `ToolExecutorAdapter.kt` | Dispatches to built-in tool handlers |
| `ToolPolicyEvaluatorAdapter.kt` | Validates tool calls against policies |

## Dependencies

- Ktor HTTP client for LLM API calls
- SQLite JDBC for memory FTS index
- Kotlinx Serialization for JSON handling
- SLF4J for logging abstraction

## Bootstrap Files

DefaultContextAssembler injects these files from workspace into prompts (max 20K chars each):
- AGENTS.md, SOUL.md, TOOLS.md, IDENTITY.md, USER.md, HEARTBEAT.md, BOOTSTRAP.md
