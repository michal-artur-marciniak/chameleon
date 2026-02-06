package com.chameleon.application.memory

import com.chameleon.memory.domain.MemoryIndex
import com.chameleon.memory.domain.MemorySearchService
import com.chameleon.session.domain.Message
import com.chameleon.session.domain.MessageRole

class MemoryContextAssembler(
    private val memoryIndex: MemoryIndex,
    private val memorySearchService: MemorySearchService
) {
    fun buildContext(messages: List<Message>, maxResults: Int = 5): String {
        val userText = messages.lastOrNull { it.role == MessageRole.USER }?.content
            ?: return ""

        return memorySearchService.searchForContext(
            memoryIndex = memoryIndex,
            userQuery = userText,
            contextWindowSize = maxResults
        )
    }
}
