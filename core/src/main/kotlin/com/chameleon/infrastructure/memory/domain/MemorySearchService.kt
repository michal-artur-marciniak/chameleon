package com.chameleon.memory.domain

/**
 * Domain service for memory search operations.
 * 
 * This service provides higher-level search capabilities including:
 * - Hybrid search combining FTS and semantic similarity
 * - Query preprocessing and normalization
 * - Result ranking and deduplication
 */
class MemorySearchService(
    private val config: MemoryConfig = MemoryConfig()
) {
    /**
     * Performs a hybrid search using the memory index.
     * 
     * @param memoryIndex The memory index aggregate
     * @param query The search query
     * @param maxResults Maximum number of results
     * @return Search results ordered by relevance
     */
    fun search(
        memoryIndex: MemoryIndex,
        query: String,
        maxResults: Int = config.defaultMaxSearchResults
    ): List<MemorySearchResult> {
        val searchQuery = MemorySearchQuery(
            query = preprocessQuery(query),
            maxResults = maxResults,
            minRelevanceScore = config.minSearchRelevanceScore
        )

        val (results, _) = memoryIndex.search(searchQuery)
        return results
    }

    /**
     * Searches for context relevant to a user query.
     * This is the primary method used by AgentLoop to fetch memory context.
     * 
     * @param memoryIndex The memory index aggregate
     * @param userQuery The user's query or message
     * @param contextWindowSize Number of results to include in context
     * @return Formatted context string for LLM consumption
     */
    fun searchForContext(
        memoryIndex: MemoryIndex,
        userQuery: String,
        contextWindowSize: Int = 5
    ): String {
        val results = search(memoryIndex, userQuery, contextWindowSize)
        
        if (results.isEmpty()) {
            return ""
        }

        return buildString {
            appendLine("## Relevant Context from Memory")
            appendLine()
            
            results.forEachIndexed { index, result ->
                appendLine("### [${index + 1}] ${result.chunk.filePath} (lines ${result.chunk.startLine + 1}-${result.chunk.endLine + 1})")
                appendLine("```")
                appendLine(result.chunk.text)
                appendLine("```")
                appendLine()
            }
        }
    }

    /**
     * Searches within a specific file or directory.
     * 
     * @param memoryIndex The memory index aggregate
     * @param query The search query
     * @param filePath The file path to search within
     * @return Search results filtered to the specified file
     */
    fun searchInFile(
        memoryIndex: MemoryIndex,
        query: String,
        filePath: String
    ): List<MemorySearchResult> {
        val searchQuery = MemorySearchQuery(
            query = preprocessQuery(query),
            maxResults = config.defaultMaxSearchResults,
            minRelevanceScore = config.minSearchRelevanceScore,
            fileFilter = listOf(filePath)
        )

        val (results, _) = memoryIndex.search(searchQuery)
        return results
    }

    /**
     * Preprocesses a search query for better results.
     * - Normalizes whitespace
     * - Trims to reasonable length
     * - Removes special characters that might break FTS
     */
    private fun preprocessQuery(query: String): String {
        return query
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500) // Limit query length
    }

    /**
     * Calculates a simple relevance score for ranking.
     * This is a basic implementation that can be enhanced with:
     * - TF-IDF scoring
     * - Semantic embeddings
     * - Query term frequency
     */
    fun calculateRelevance(query: String, chunk: MemoryChunk): Double {
        val queryTerms = query.lowercase().split(" ").filter { it.length > 2 }
        val chunkText = chunk.text.lowercase()
        
        if (queryTerms.isEmpty()) return 0.0

        // Simple term frequency score
        val matches = queryTerms.count { term ->
            chunkText.contains(term)
        }

        return matches.toDouble() / queryTerms.size
    }
}
