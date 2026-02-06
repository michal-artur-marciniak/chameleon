package com.chameleon.session.port

import com.chameleon.session.SessionId
import com.chameleon.session.SessionKey
import com.chameleon.session.domain.Message
import com.chameleon.session.domain.Session

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
