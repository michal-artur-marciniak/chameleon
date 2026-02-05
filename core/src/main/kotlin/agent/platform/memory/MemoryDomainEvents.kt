package agent.platform.memory

import java.time.Instant
import java.util.UUID

/**
 * Domain events emitted by the Memory aggregate.
 */
sealed interface MemoryDomainEvent {
    val eventId: String
    val timestamp: Instant

    /**
     * Emitted when a memory search is executed.
     */
    data class MemorySearchPerformed(
        val query: String,
        val resultCount: Int,
        val durationMs: Long,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Instant = Instant.now()
    ) : MemoryDomainEvent

    /**
     * Emitted when a file is indexed into memory.
     */
    data class FileIndexed(
        val filePath: String,
        val chunksIndexed: Int,
        val totalChunks: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Instant = Instant.now()
    ) : MemoryDomainEvent

    /**
     * Emitted when the memory index is cleared/rebuilt.
     */
    data class IndexCleared(
        val previousChunkCount: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Instant = Instant.now()
    ) : MemoryDomainEvent

    /**
     * Emitted when a file is removed from the memory index.
     */
    data class FileRemovedFromIndex(
        val filePath: String,
        val chunksRemoved: Int,
        override val eventId: String = UUID.randomUUID().toString(),
        override val timestamp: Instant = Instant.now()
    ) : MemoryDomainEvent
}
