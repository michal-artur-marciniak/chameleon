package com.chameleon.memory.domain

import com.chameleon.memory.port.MemoryIndexRepositoryPort
import java.io.File

/**
 * Result of indexing a file into memory.
 */
data class FileIndexResult(
    val filePath: String,
    val chunksCreated: Int,
    val success: Boolean,
    val error: String? = null
)

/**
 * MemoryIndex aggregate - Manages memory indexing and search operations.
 *
 * Invariants:
 * - Chunk IDs are content-addressed
 * - Index is rebuildable from source files
 * - Files are chunked with configurable overlap
 */
class MemoryIndex private constructor(
    val config: MemoryConfig,
    private val repository: MemoryIndexRepositoryPort
) {
    companion object {
        /**
         * Factory method: Creates a new MemoryIndex aggregate.
         * 
         * @param config The memory configuration
         * @param repository The repository port for persistence
         */
        fun create(
            config: MemoryConfig = MemoryConfig(),
            repository: MemoryIndexRepositoryPort
        ): MemoryIndex {
            return MemoryIndex(config, repository)
        }
    }

    /**
     * Indexes a single file into memory.
     * 
     * @param file The file to index
     * @return FileIndexResult with details of the indexing operation
     */
    fun indexFile(file: File): Pair<FileIndexResult, MemoryDomainEvent.FileIndexed?> {
        if (!file.exists() || !file.isFile) {
            return FileIndexResult(
                filePath = file.path,
                chunksCreated = 0,
                success = false,
                error = "File does not exist or is not a regular file"
            ) to null
        }

        if (!isSupportedFileType(file)) {
            return FileIndexResult(
                filePath = file.path,
                chunksCreated = 0,
                success = false,
                error = "Unsupported file type"
            ) to null
        }

        // Remove existing chunks for this file first
        repository.removeByFile(file.path)

        // Read file and create chunks
        val lines = file.readLines()
        val chunks = createChunks(file.path, lines)

        // Index chunks
        val indexedCount = repository.indexChunks(chunks)

        val result = FileIndexResult(
            filePath = file.path,
            chunksCreated = indexedCount,
            success = true
        )

        val event = MemoryDomainEvent.FileIndexed(
            filePath = file.path,
            chunksIndexed = indexedCount,
            totalChunks = repository.status().totalChunks
        )

        return result to event
    }

    /**
     * Indexes multiple files into memory.
     * 
     * @param files The files to index
     * @return List of FileIndexResult for each file
     */
    fun indexFiles(files: List<File>): List<Pair<FileIndexResult, MemoryDomainEvent.FileIndexed?>> {
        return files.map { indexFile(it) }
    }

    /**
     * Searches the memory index with the given query.
     * 
     * @param query The search query
     * @return Pair of (search results, search performed event)
     */
    fun search(query: MemorySearchQuery): Pair<List<MemorySearchResult>, MemoryDomainEvent.MemorySearchPerformed> {
        val startTime = System.currentTimeMillis()
        
        val results = repository.search(query)
        
        val durationMs = System.currentTimeMillis() - startTime
        
        val event = MemoryDomainEvent.MemorySearchPerformed(
            query = query.query,
            resultCount = results.size,
            durationMs = durationMs
        )

        return results to event
    }

    /**
     * Convenience method: Search by string query.
     * 
     * @param query The search string
     * @param maxResults Maximum number of results to return
     * @return Pair of (search results, search performed event)
     */
    fun search(query: String, maxResults: Int = config.defaultMaxSearchResults): 
        Pair<List<MemorySearchResult>, MemoryDomainEvent.MemorySearchPerformed> {
        return search(MemorySearchQuery(
            query = query,
            maxResults = maxResults,
            minRelevanceScore = config.minSearchRelevanceScore
        ))
    }

    /**
     * Gets the current status of the memory index.
     */
    fun status(): MemoryIndexStatus {
        return repository.status()
    }

    /**
     * Clears all indexed chunks.
     * 
     * @return Pair of (number of chunks removed, index cleared event)
     */
    fun clear(): Pair<Int, MemoryDomainEvent.IndexCleared> {
        val previousStatus = repository.status()
        val removedCount = repository.clear()
        
        val event = MemoryDomainEvent.IndexCleared(
            previousChunkCount = previousStatus.totalChunks
        )

        return removedCount to event
    }

    /**
     * Removes a file from the index.
     * 
     * @param filePath The file path to remove
     * @return Pair of (number of chunks removed, file removed event) or null if file not indexed
     */
    fun removeFile(filePath: String): Pair<Int, MemoryDomainEvent.FileRemovedFromIndex>? {
        if (!repository.isFileIndexed(filePath)) {
            return null
        }

        val chunksRemoved = repository.removeByFile(filePath)
        
        val event = MemoryDomainEvent.FileRemovedFromIndex(
            filePath = filePath,
            chunksRemoved = chunksRemoved
        )

        return chunksRemoved to event
    }

    /**
     * Checks if a file type is supported for indexing.
     */
    private fun isSupportedFileType(file: File): Boolean {
        val extension = file.extension.lowercase()
        return config.supportedFileExtensions.contains(extension)
    }

    /**
     * Creates chunks from file lines with overlap.
     */
    private fun createChunks(filePath: String, lines: List<String>): List<MemoryChunk> {
        val chunks = mutableListOf<MemoryChunk>()
        val chunkSize = config.chunkSizeLines
        val overlap = config.chunkOverlapLines

        var startLine = 0
        while (startLine < lines.size && chunks.size < config.maxChunksPerFile) {
            val endLine = minOf(startLine + chunkSize, lines.size)
            val chunkLines = lines.subList(startLine, endLine)
            val text = chunkLines.joinToString("\n")

            if (text.isNotBlank()) {
                val chunkId = MemoryChunk.generateChunkId(filePath, startLine, endLine - 1, text)
                chunks.add(MemoryChunk(
                    chunkId = chunkId,
                    filePath = filePath,
                    startLine = startLine,
                    endLine = endLine - 1,
                    text = text
                ))
            }

            // Move to next chunk with overlap
            startLine += (chunkSize - overlap)
        }

        return chunks
    }
}
