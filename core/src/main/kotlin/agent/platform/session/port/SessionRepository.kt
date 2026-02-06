package agent.platform.session.port

import agent.platform.session.SessionId
import agent.platform.session.SessionKey
import agent.platform.session.domain.Message
import agent.platform.session.domain.Session

interface SessionRepository {
    fun findByKey(key: SessionKey): Session?
    fun findById(id: SessionId): Session?
    fun save(session: Session)
    fun appendMessage(sessionId: SessionId, message: Message)
    fun listAll(): List<SessionSummary>
}

data class SessionSummary(
    val id: SessionId,
    val key: SessionKey,
    val updatedAt: Long,
    val messageCount: Int
)
