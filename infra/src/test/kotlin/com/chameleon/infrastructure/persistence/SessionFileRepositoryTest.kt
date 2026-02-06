package com.chameleon.infrastructure.persistence

import com.chameleon.session.domain.PeerType
import com.chameleon.session.domain.SessionId
import com.chameleon.session.domain.SessionKey
import com.chameleon.session.domain.Message
import com.chameleon.session.domain.MessageRole
import com.chameleon.session.domain.Session
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SessionFileRepositoryTest {
    @Test
    fun savesAndLoadsSession() {
        val workspace = Files.createTempDirectory("session-repo-test")
        val repo = SessionFileRepository(workspace, Json { encodeDefaults = true })

        val key = SessionKey(
            agentId = "main",
            channel = "telegram",
            peerType = PeerType.DM,
            peerId = "123"
        )
        val session = Session(
            id = SessionId.generate(),
            key = key,
            messages = listOf(Message(role = MessageRole.USER, content = "hello"))
        )

        repo.save(session)

        val loaded = repo.findById(session.id)
        assertNotNull(loaded)
        assertEquals(session.id.value, loaded.id.value)
        assertEquals(key.toKeyString(), loaded.key.toKeyString())
        assertEquals(1, loaded.messages.size)
        assertEquals("hello", loaded.messages.first().content)
    }
}
