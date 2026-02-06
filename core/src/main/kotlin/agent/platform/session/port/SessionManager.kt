package agent.platform.session.port

import agent.platform.session.SessionId
import agent.platform.session.SessionKey
import agent.platform.session.domain.Message
import agent.platform.session.domain.Session

interface SessionManager {
    suspend fun withSessionLock(key: SessionKey, block: suspend (Session) -> Unit)
    suspend fun loadOrCreate(key: SessionKey): Session
    suspend fun append(sessionId: SessionId, message: Message)
    suspend fun maybeCompact(session: Session): CompactionResult?
}

data class CompactionResult(
    val summaryMessage: Message,
    val prunedToolCount: Int
)
