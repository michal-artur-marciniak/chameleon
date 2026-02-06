package com.chameleon.session

import com.chameleon.persistence.SessionFileRepository
import com.chameleon.session.domain.Message
import com.chameleon.session.domain.Session
import com.chameleon.session.domain.SessionId
import com.chameleon.session.domain.SessionKey
import com.chameleon.session.port.CompactionResult
import com.chameleon.session.port.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default implementation of [SessionManager] with per-session locking.
 *
 * Ensures thread-safe access to sessions using a mutex-per-key strategy.
 * Automatically creates sessions on demand.
 */
class DefaultSessionManager(
    private val repository: SessionFileRepository
) : SessionManager {
    private val locks = mutableMapOf<String, Mutex>()

    override suspend fun withSessionLock(key: SessionKey, block: suspend (Session) -> Unit) {
        val mutex = synchronized(locks) {
            locks.getOrPut(key.toKeyString()) { Mutex() }
        }
        mutex.withLock {
            val session = loadOrCreate(key)
            block(session)
        }
    }

    override suspend fun loadOrCreate(key: SessionKey): Session {
        val existing = repository.findByKey(key)
        if (existing != null) return existing
        val created = Session(id = SessionId.generate(), key = key)
        repository.save(created)
        return created
    }

    override suspend fun append(sessionId: SessionId, message: Message) {
        repository.appendMessage(sessionId, message)
    }

    override suspend fun maybeCompact(session: Session): CompactionResult? {
        return null
    }
}
