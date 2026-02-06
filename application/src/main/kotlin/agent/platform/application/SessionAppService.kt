package agent.platform.application

import agent.platform.session.CompactionResult
import agent.platform.session.Session
import agent.platform.session.SessionManager

class SessionAppService(
    private val sessionManager: SessionManager
) {
    suspend fun maybeCompact(session: Session): CompactionResult? {
        val result = sessionManager.maybeCompact(session)
        return result
    }
}
