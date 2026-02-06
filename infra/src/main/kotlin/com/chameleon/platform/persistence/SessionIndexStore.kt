package com.chameleon.persistence

import com.chameleon.session.domain.SessionMetadata
import kotlinx.serialization.Serializable

@Serializable
data class SessionIndexEntry(
    val sessionId: String,
    val sessionKey: String,
    val updatedAt: Long,
    val displayName: String? = null,
    val messageCount: Int = 0,
    val thinkingLevel: String? = null,
    val verboseLevel: String? = null,
    val modelOverride: String? = null,
    val groupActivation: String? = null
) {
    fun toMetadata(): SessionMetadata {
        return SessionMetadata(
            updatedAt = updatedAt,
            displayName = displayName,
            thinkingLevel = thinkingLevel,
            verboseLevel = verboseLevel,
            modelOverride = modelOverride,
            groupActivation = groupActivation
        )
    }
}
