package com.chameleon.session

import com.chameleon.session.domain.PeerType
import com.chameleon.session.domain.SessionKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SessionKeyTest {
    @Test
    fun parsesDmKey() {
        val key = "agent:main:telegram:dm:123"
        val parsed = SessionKey.parse(key)

        assertEquals("main", parsed.agentId)
        assertEquals("telegram", parsed.channel)
        assertEquals(PeerType.DM, parsed.peerType)
        assertEquals("123", parsed.peerId)
        assertNull(parsed.threadId)
        assertEquals(key, parsed.toKeyString())
    }

    @Test
    fun parsesGroupThreadKey() {
        val key = "agent:main:telegram:group:999:thread:456"
        val parsed = SessionKey.parse(key)

        assertEquals(PeerType.GROUP, parsed.peerType)
        assertEquals("456", parsed.threadId)
        assertEquals(key, parsed.toKeyString())
    }

    @Test
    fun rejectsInvalidKey() {
        assertFailsWith<IllegalArgumentException> {
            SessionKey.parse("invalid")
        }
    }
}
