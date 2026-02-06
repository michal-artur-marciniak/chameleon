package com.chameleon.memory

import com.chameleon.memory.domain.MemoryChunk
import com.chameleon.memory.domain.MemoryIndexStatus
import com.chameleon.memory.domain.MemorySearchQuery
import com.chameleon.memory.domain.MemorySearchResult
import com.chameleon.memory.port.MemoryIndexRepositoryPort
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * SQLite FTS-backed adapter for MemoryIndexRepositoryPort.
 * 
 * This adapter implements full-text search using SQLite's FTS5 extension.
 * It provides efficient text search capabilities over indexed memory chunks.
 * 
 * Database Schema:
 * - memory_chunks: Main table for chunk metadata
 * - memory_fts: FTS5 virtual table for full-text search
 */
class SqliteMemoryIndexAdapter(
    private val dbPath: Path
) : MemoryIndexRepositoryPort {

    private val connection: Connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }

    init {
        initializeSchema()
    }

    /**
     * Initializes the database schema with FTS5 support.
     */
    private fun initializeSchema() {
        connection.createStatement().use { stmt ->
            // Main chunks table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS memory_chunks (
                    chunk_id TEXT PRIMARY KEY,
                    file_path TEXT NOT NULL,
                    start_line INTEGER NOT NULL,
                    end_line INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    indexed_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
                )
                """.trimIndent()
            )

            // Index on file_path for fast file lookups
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_chunks_file_path 
                ON memory_chunks(file_path)
                """.trimIndent()
            )

            // FTS5 virtual table for full-text search
            stmt.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS memory_fts USING fts5(
                    chunk_id,
                    text,
                    content='memory_chunks',
                    content_rowid='rowid'
                )
                """.trimIndent()
            )

            // Triggers to keep FTS index in sync
            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS memory_chunks_ai AFTER INSERT ON memory_chunks BEGIN
                    INSERT INTO memory_fts(chunk_id, text) VALUES (new.chunk_id, new.text);
                END
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS memory_chunks_ad AFTER DELETE ON memory_chunks BEGIN
                    INSERT INTO memory_fts(memory_fts, rowid, chunk_id, text) VALUES ('delete', old.rowid, old.chunk_id, old.text);
                END
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS memory_chunks_au AFTER UPDATE ON memory_chunks BEGIN
                    INSERT INTO memory_fts(memory_fts, rowid, chunk_id, text) VALUES ('delete', old.rowid, old.chunk_id, old.text);
                    INSERT INTO memory_fts(chunk_id, text) VALUES (new.chunk_id, new.text);
                END
                """.trimIndent()
            )
        }
    }

    override fun indexChunks(chunks: List<MemoryChunk>): Int {
        if (chunks.isEmpty()) return 0

        var indexedCount = 0
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO memory_chunks 
            (chunk_id, file_path, start_line, end_line, text, indexed_at) 
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            for (chunk in chunks) {
                stmt.setString(1, chunk.chunkId)
                stmt.setString(2, chunk.filePath)
                stmt.setInt(3, chunk.startLine)
                stmt.setInt(4, chunk.endLine)
                stmt.setString(5, chunk.text)
                stmt.setLong(6, chunk.indexedAt)
                stmt.addBatch()
            }
            val results = stmt.executeBatch()
            indexedCount = results.count { it >= 0 }
        }

        return indexedCount
    }

    override fun search(query: MemorySearchQuery): List<MemorySearchResult> {
        val searchQuery = buildSearchQuery(query.query)
        
        return connection.prepareStatement(
            """
            SELECT c.chunk_id, c.file_path, c.start_line, c.end_line, c.text, c.indexed_at,
                   rank
            FROM memory_fts f
            JOIN memory_chunks c ON f.chunk_id = c.chunk_id
            WHERE memory_fts MATCH ?
            ORDER BY rank
            LIMIT ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, searchQuery)
            stmt.setInt(2, query.maxResults)
            
            val results = mutableListOf<MemorySearchResult>()
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val chunk = mapResultSetToChunk(rs)
                    val rank = rs.getDouble("rank")
                    // Convert rank (lower is better in FTS5) to relevance score (higher is better)
                    val relevanceScore = calculateRelevanceFromRank(rank)
                    
                    if (relevanceScore >= query.minRelevanceScore) {
                        results.add(MemorySearchResult(
                            chunk = chunk,
                            relevanceScore = relevanceScore,
                            matchType = MemorySearchResult.MatchType.FULL_TEXT
                        ))
                    }
                }
            }
            results
        }
    }

    override fun status(): MemoryIndexStatus {
        val totalChunks = connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM memory_chunks").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }

        val totalFiles = connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(DISTINCT file_path) FROM memory_chunks").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }

        val lastIndexedAt = connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT MAX(indexed_at) FROM memory_chunks").use { rs ->
                if (rs.next()) rs.getLong(1).takeIf { it > 0 } else null
            }
        }

        return MemoryIndexStatus(
            totalChunks = totalChunks,
            totalFiles = totalFiles,
            lastIndexedAt = lastIndexedAt,
            isIndexing = false
        )
    }

    override fun clear(): Int {
        val count = status().totalChunks
        
        connection.createStatement().use { stmt ->
            // Clear FTS index first
            stmt.execute("DELETE FROM memory_fts")
            // Clear main table
            stmt.execute("DELETE FROM memory_chunks")
        }
        
        return count
    }

    override fun removeByFile(filePath: String): Int {
        var count = 0
        
        // Get count before deletion
        connection.prepareStatement(
            "SELECT COUNT(*) FROM memory_chunks WHERE file_path = ?"
        ).use { stmt ->
            stmt.setString(1, filePath)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    count = rs.getInt(1)
                }
            }
        }

        // Delete from main table (triggers will handle FTS)
        connection.prepareStatement(
            "DELETE FROM memory_chunks WHERE file_path = ?"
        ).use { stmt ->
            stmt.setString(1, filePath)
            stmt.executeUpdate()
        }

        return count
    }

    override fun getChunksByFile(filePath: String): List<MemoryChunk> {
        return connection.prepareStatement(
            """
            SELECT chunk_id, file_path, start_line, end_line, text, indexed_at
            FROM memory_chunks
            WHERE file_path = ?
            ORDER BY start_line
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, filePath)
            
            val chunks = mutableListOf<MemoryChunk>()
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    chunks.add(mapResultSetToChunk(rs))
                }
            }
            chunks
        }
    }

    override fun isFileIndexed(filePath: String): Boolean {
        return connection.prepareStatement(
            "SELECT 1 FROM memory_chunks WHERE file_path = ? LIMIT 1"
        ).use { stmt ->
            stmt.setString(1, filePath)
            stmt.executeQuery().use { rs ->
                rs.next()
            }
        }
    }

    /**
     * Closes the database connection.
     */
    fun close() {
        connection.close()
    }

    /**
     * Builds an FTS5 search query from user input.
     * Handles escaping and query syntax.
     */
    private fun buildSearchQuery(query: String): String {
        // Escape special FTS5 characters
        return query
            .replace("\"", "\"") // Escape quotes
            .replace("*", "")      // Remove wildcards for now
            .replace("-", " ")     // Replace negation with space
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { "${it}*" } // Add prefix matching
    }

    /**
     * Converts FTS5 rank to a relevance score between 0 and 1.
     * FTS5 rank is usually negative (lower = better match).
     */
    private fun calculateRelevanceFromRank(rank: Double): Double {
        // FTS5 rank is typically negative, with values closer to 0 being better
        // Convert to 0-1 scale where 1 is best match
        return when {
            rank >= 0 -> 1.0
            rank < -10 -> 0.1
            else -> 1.0 + (rank / 10.0) // Linear interpolation
        }
    }

    private fun mapResultSetToChunk(rs: ResultSet): MemoryChunk {
        return MemoryChunk(
            chunkId = rs.getString("chunk_id"),
            filePath = rs.getString("file_path"),
            startLine = rs.getInt("start_line"),
            endLine = rs.getInt("end_line"),
            text = rs.getString("text"),
            indexedAt = rs.getLong("indexed_at")
        )
    }
}
