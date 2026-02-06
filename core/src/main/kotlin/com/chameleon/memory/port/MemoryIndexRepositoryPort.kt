package com.chameleon.memory.port

import com.chameleon.memory.domain.MemoryChunk
import com.chameleon.memory.domain.MemoryIndexStatus
import com.chameleon.memory.domain.MemorySearchQuery
import com.chameleon.memory.domain.MemorySearchResult

/**
 * Port for memory index repository operations.
 *
 * The repository is responsible for:
 * - Storing memory chunks with full-text search capability
 * - Searching indexed content
 * - Managing index lifecycle (clear, rebuild)
 */
interface MemoryIndexRepositoryPort {
    /**
     * Indexes a list of memory chunks.
     * Chunks with existing IDs are updated (upsert behavior).
     *
     * @param chunks The chunks to index
     * @return Number of chunks indexed
     */
    fun indexChunks(chunks: List<MemoryChunk>): Int

    /**
     * Searches the memory index for chunks matching the query.
     *
     * @param query The search query
     * @return List of search results ordered by relevance
     */
    fun search(query: MemorySearchQuery): List<MemorySearchResult>

    /**
     * Gets the current status of the memory index.
     */
    fun status(): MemoryIndexStatus

    /**
     * Clears all indexed chunks.
     *
     * @return Number of chunks removed
     */
    fun clear(): Int

    /**
     * Removes all chunks for a specific file.
     *
     * @param filePath The file path to remove
     * @return Number of chunks removed
     */
    fun removeByFile(filePath: String): Int

    /**
     * Gets all chunks for a specific file.
     *
     * @param filePath The file path
     * @return List of chunks for that file
     */
    fun getChunksByFile(filePath: String): List<MemoryChunk>

    /**
     * Checks if a file has been indexed.
     *
     * @param filePath The file path
     * @return true if the file has any chunks in the index
     */
    fun isFileIndexed(filePath: String): Boolean
}
