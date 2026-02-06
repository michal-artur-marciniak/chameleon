package com.chameleon.persistence

import com.chameleon.session.domain.SessionId
import com.chameleon.session.domain.SessionKey
import com.chameleon.session.domain.Message
import com.chameleon.session.domain.Session
import com.chameleon.session.port.SessionRepository
import com.chameleon.session.port.SessionSummary
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * File-based implementation of [SessionRepository] using JSONL format.
 *
 * Storage layout:
 * - sessions/sessions.json: Index file mapping session IDs to metadata
 * - sessions/{sessionId}.jsonl: Message logs in JSONL format
 *
 * Thread-safe via synchronized blocks on file operations.
 */
class SessionFileRepository(
    private val workspaceDir: Path,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
) : SessionRepository {
    private val lock = Any()
    private val sessionsDir = workspaceDir.resolve("sessions")
    private val indexPath = sessionsDir.resolve("sessions.json")
    private val mapSerializer = MapSerializer(String.serializer(), SessionIndexEntry.serializer())

    override fun findByKey(key: SessionKey): Session? {
        val index = readIndex()
        val entry = index.values.firstOrNull { it.sessionKey == key.toKeyString() } ?: return null
        return loadSession(entry)
    }

    override fun findById(id: SessionId): Session? {
        val entry = readIndex()[id.value] ?: return null
        return loadSession(entry)
    }

    override fun save(session: Session) {
        synchronized(lock) {
            Files.createDirectories(sessionsDir)
            writeMessages(session)
            val index = readIndex().toMutableMap()
            index[session.id.value] = toEntry(session)
            writeIndex(index)
        }
    }

    override fun appendMessage(sessionId: SessionId, message: Message) {
        synchronized(lock) {
            Files.createDirectories(sessionsDir)
            val sessionPath = sessionsDir.resolve("${sessionId.value}.jsonl")
            val line = json.encodeToString(Message.serializer(), message)
            Files.writeString(
                sessionPath,
                line + "\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
            val index = readIndex().toMutableMap()
            val entry = index[sessionId.value]
            if (entry != null) {
                index[sessionId.value] = entry.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = entry.messageCount + 1
                )
                writeIndex(index)
            }
        }
    }

    override fun listAll(): List<SessionSummary> {
        return readIndex().values.map { entry ->
            SessionSummary(
                id = SessionId(entry.sessionId),
                key = SessionKey.parse(entry.sessionKey),
                updatedAt = entry.updatedAt,
                messageCount = entry.messageCount
            )
        }
    }

    private fun loadSession(entry: SessionIndexEntry): Session {
        val sessionPath = sessionsDir.resolve("${entry.sessionId}.jsonl")
        val messages = if (Files.exists(sessionPath)) {
            Files.readAllLines(sessionPath).mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) null else runCatching {
                    json.decodeFromString(Message.serializer(), trimmed)
                }.getOrNull()
            }
        } else {
            emptyList()
        }
        return Session(
            id = SessionId(entry.sessionId),
            key = SessionKey.parse(entry.sessionKey),
            messages = messages,
            metadata = entry.toMetadata()
        )
    }

    private fun writeMessages(session: Session) {
        val sessionPath = sessionsDir.resolve("${session.id.value}.jsonl")
        val lines = session.messages.map { json.encodeToString(Message.serializer(), it) }
        Files.write(sessionPath, lines)
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

    private fun toEntry(session: Session): SessionIndexEntry {
        return SessionIndexEntry(
            sessionId = session.id.value,
            sessionKey = session.key.toKeyString(),
            updatedAt = session.metadata.updatedAt,
            displayName = session.metadata.displayName,
            messageCount = session.messages.size,
            thinkingLevel = session.metadata.thinkingLevel,
            verboseLevel = session.metadata.verboseLevel,
            modelOverride = session.metadata.modelOverride,
            groupActivation = session.metadata.groupActivation
        )
    }
}
