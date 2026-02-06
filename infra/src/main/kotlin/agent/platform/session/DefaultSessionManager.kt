package agent.platform.session

import agent.platform.persistence.SessionFileRepository
import agent.platform.session.domain.Message
import agent.platform.session.domain.Session
import agent.platform.session.port.CompactionResult
import agent.platform.session.port.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
