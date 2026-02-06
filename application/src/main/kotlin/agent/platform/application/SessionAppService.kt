package agent.platform.application

import agent.platform.session.domain.Session
import agent.platform.session.port.CompactionResult
import agent.platform.session.port.SessionManager

class SessionAppService(
    private val sessionManager: SessionManager
) {
    suspend fun maybeCompact(session: Session): CompactionResult? {
        val result = sessionManager.maybeCompact(session)
        return result
    }
}
