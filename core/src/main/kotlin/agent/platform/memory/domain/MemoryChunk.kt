package agent.platform.memory.domain

/**
 * Value object representing a chunk of indexed memory content.
 * Chunk IDs are content-addressed (hash of content + location).
 */
data class MemoryChunk(
    val chunkId: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val text: String,
    val indexedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Generates a content-addressed chunk ID from file path and content.
         * This ensures idempotent indexing - same content = same ID.
         */
        fun generateChunkId(filePath: String, startLine: Int, endLine: Int, text: String): String {
            val content = "$filePath:$startLine:$endLine:$text"
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(content.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16) // First 16 chars are sufficient for uniqueness
        }
    }

    init {
        require(startLine >= 0) { "startLine must be non-negative" }
        require(endLine >= startLine) { "endLine must be >= startLine" }
        require(text.isNotBlank()) { "text must not be blank" }
    }
}

/**
 * Result of a memory search operation.
 */
data class MemorySearchResult(
    val chunk: MemoryChunk,
    val relevanceScore: Double,
    val matchType: MatchType
) {
    enum class MatchType {
        FULL_TEXT,
        SEMANTIC,
        HYBRID
    }
}

/**
 * Query for memory search operations.
 */
data class MemorySearchQuery(
    val query: String,
    val maxResults: Int = 10,
    val minRelevanceScore: Double = 0.0,
    val fileFilter: List<String>? = null
)

/**
 * Metadata about the memory index state.
 */
data class MemoryIndexStatus(
    val totalChunks: Int,
    val totalFiles: Int,
    val lastIndexedAt: Long?,
    val isIndexing: Boolean
)
