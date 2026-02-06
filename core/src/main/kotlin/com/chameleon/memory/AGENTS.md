# Memory Module Conventions

## Domain Aggregate Pattern

The MemoryIndex aggregate lives in `MemoryIndex.kt` and manages memory indexing and search operations.

### Key Patterns

**1. Domain Aggregate with Factory Method**
```kotlin
// Create via factory method with repository
val memoryIndex = MemoryIndex.create(
    config = MemoryConfig(),
    repository = sqliteAdapter
)

// Index a file
val (result, event) = memoryIndex.indexFile(file)

// Search memory
val (results, event) = memoryIndex.search("query", maxResults = 5)
```

**2. Content-Addressed Chunk IDs**
Chunk IDs are generated from file path and content hash for idempotent indexing:
```kotlin
val chunkId = MemoryChunk.generateChunkId(
    filePath = "/path/to/file.kt",
    startLine = 0,
    endLine = 50,
    text = "file content..."
)
```

**3. Domain Events**
All operations return domain events for observability:
- `FileIndexed` - Emitted when a file is indexed
- `MemorySearchPerformed` - Emitted when search is executed
- `IndexCleared` - Emitted when index is cleared
- `FileRemovedFromIndex` - Emitted when file is removed

## Repository Port

`MemoryIndexRepositoryPort` defines the interface for persistence:

```kotlin
interface MemoryIndexRepositoryPort {
    fun indexChunks(chunks: List<MemoryChunk>): Int
    fun search(query: MemorySearchQuery): List<MemorySearchResult>
    fun status(): MemoryIndexStatus
    fun clear(): Int
    fun removeByFile(filePath: String): Int
    fun getChunksByFile(filePath: String): List<MemoryChunk>
    fun isFileIndexed(filePath: String): Boolean
}
```

## Infrastructure Adapter

`SqliteMemoryIndexAdapter` implements FTS-backed search using SQLite FTS5:

```kotlin
val adapter = SqliteMemoryIndexAdapter(
    dbPath = Paths.get("/workspace/memory.db")
)

// Index chunks
val indexed = adapter.indexChunks(chunks)

// Search
val results = adapter.search(MemorySearchQuery("query"))
```

### Database Schema

The adapter creates three main structures:
1. **memory_chunks** - Main table for chunk metadata
2. **memory_fts** - FTS5 virtual table for full-text search
3. **Triggers** - Keep FTS index in sync with main table

## Configuration

`MemoryConfig` controls indexing behavior:

```kotlin
MemoryConfig(
    chunkSizeLines = 50,          // Lines per chunk
    chunkOverlapLines = 5,        // Overlap between chunks
    maxChunksPerFile = 1000,      // Limit per file
    defaultMaxSearchResults = 10, // Default result limit
    minSearchRelevanceScore = 0.0 // Minimum relevance threshold
)
```

## Invariants

1. **Chunk IDs are content-addressed** - Same content = same ID
2. **Index is rebuildable from source files** - Chunks can be recreated
3. **Files are chunked with overlap** - Configurable overlap prevents context loss
4. **FTS index stays synchronized** - Triggers maintain consistency

## File Organization

- `MemoryIndex.kt` - Domain aggregate with indexing logic
- `MemoryChunk.kt` - Value objects (chunks, search results, queries)
- `MemoryDomainEvents.kt` - Domain events for memory operations
- `MemorySearchService.kt` - Domain service for search operations
- `MemoryIndexRepositoryPort.kt` - Repository port interface
- `MemoryConfig.kt` - Configuration data class
- `SqliteMemoryIndexAdapter.kt` - SQLite FTS adapter (infra module)

## When Modifying Memory Module

1. Always return domain events for state changes
2. Maintain idempotent indexing (content-addressed IDs)
3. Update `MemoryConfig` if adding new configuration options
4. Ensure FTS triggers stay in sync with schema changes
5. Test with large files and edge cases

## Integration with AgentTurnService

Memory search is composed in the application layer. The domain aggregate stays pure;
memory context is injected when building the LLM request:

```kotlin
val memoryContext = memoryContextAssembler.buildContext(context.messages)
val requestPlan = llmRequestBuilder.build(context, modelId, memoryContext)
```

## Testing

Key test scenarios:
- Chunk creation with overlap
- Content-addressed ID generation
- FTS search with various queries
- File removal and re-indexing
- Concurrent indexing operations
- Large file handling
