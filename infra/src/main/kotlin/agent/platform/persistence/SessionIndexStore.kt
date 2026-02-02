package agent.platform.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
data class SessionIndexEntry(
    val sessionId: String,
    val updatedAt: Long,
    val displayName: String? = null
)

class SessionIndexStore(
    private val workspaceDir: Path,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
) {
    private val lock = Any()
    private val sessionsDir = workspaceDir.resolve("sessions")
    private val indexPath = sessionsDir.resolve("sessions.json")
    private val mapSerializer = MapSerializer(String.serializer(), SessionIndexEntry.serializer())

    fun touchSession(sessionKey: String, displayName: String? = null): SessionIndexEntry {
        synchronized(lock) {
            Files.createDirectories(sessionsDir)
            val index = readIndex().toMutableMap()
            val now = System.currentTimeMillis()
            val existing = index[sessionKey]
            val updated = if (existing != null) {
                existing.copy(
                    updatedAt = now,
                    displayName = displayName ?: existing.displayName
                )
            } else {
                SessionIndexEntry(
                    sessionId = UUID.randomUUID().toString(),
                    updatedAt = now,
                    displayName = displayName
                )
            }
            index[sessionKey] = updated
            writeIndex(index)
            return updated
        }
    }

    private fun readIndex(): Map<String, SessionIndexEntry> {
        if (!Files.exists(indexPath)) return emptyMap()
        val content = Files.readString(indexPath).trim()
        if (content.isEmpty()) return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, content) }
            .getOrElse { emptyMap() }
    }

    private fun writeIndex(index: Map<String, SessionIndexEntry>) {
        val tempPath = indexPath.resolveSibling("sessions.json.tmp")
        val content = json.encodeToString(mapSerializer, index)
        Files.writeString(tempPath, content)
        Files.move(
            tempPath,
            indexPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }
}
