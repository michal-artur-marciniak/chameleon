package com.chameleon.session.application

import com.chameleon.session.domain.Session
import com.chameleon.session.port.CompactionResult
import com.chameleon.session.port.SessionManager

/**
 * Application service for session management operations.
 *
 * Provides a thin layer over [SessionManager] for use cases that need
 * to trigger session maintenance operations like compaction.
 *
 * @property sessionManager The underlying session manager
 */
class SessionAppService(
    private val sessionManager: SessionManager
) {

    /**
     * Triggers session compaction if the session exceeds configured thresholds.
     *
     * Compaction summarizes old messages to reduce token usage while preserving
     * conversation context.
     *
     * @param session The session to potentially compact
     * @return Compaction result if compaction occurred, null if not needed
     */
    suspend fun maybeCompact(session: Session): CompactionResult? {
        val result = sessionManager.maybeCompact(session)
        return result
    }
}
