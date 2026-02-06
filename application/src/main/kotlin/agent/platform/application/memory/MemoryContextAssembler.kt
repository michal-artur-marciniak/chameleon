package agent.platform.application.memory

import agent.platform.memory.domain.MemoryIndex
import agent.platform.memory.domain.MemorySearchService
import agent.platform.session.domain.Message
import agent.platform.session.domain.MessageRole

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
