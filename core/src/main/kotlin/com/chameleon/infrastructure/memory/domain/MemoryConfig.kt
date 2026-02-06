package com.chameleon.memory.domain

/**
 * Configuration for memory indexing and search behavior.
 */
data class MemoryConfig(
    val chunkSizeLines: Int = 50,
    val chunkOverlapLines: Int = 5,
    val maxChunksPerFile: Int = 1000,
    val defaultMaxSearchResults: Int = 10,
    val minSearchRelevanceScore: Double = 0.0,
    val enableAutoIndexing: Boolean = true,
    val supportedFileExtensions: List<String> = listOf(
        "md", "txt", "kt", "java", "py", "js", "ts", "json", "yaml", "yml", "xml"
    )
)
