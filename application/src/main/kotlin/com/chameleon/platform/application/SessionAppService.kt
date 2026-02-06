package com.chameleon.application

import com.chameleon.session.domain.Session
import com.chameleon.session.port.CompactionResult
import com.chameleon.session.port.SessionManager

class SessionAppService(
    private val sessionManager: SessionManager
) {
    suspend fun maybeCompact(session: Session): CompactionResult? {
        val result = sessionManager.maybeCompact(session)
        return result
    }
}
