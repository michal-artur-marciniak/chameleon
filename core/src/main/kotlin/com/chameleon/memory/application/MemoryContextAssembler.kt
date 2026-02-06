package com.chameleon.memory.application

import com.chameleon.memory.domain.MemoryIndex
import com.chameleon.memory.domain.MemorySearchService
import com.chameleon.session.domain.Message
import com.chameleon.session.domain.MessageRole

/**
 * Assembles memory context from the memory system for injection into prompts.
 *
 * Searches for relevant memories based on the current user message and formats
 * them for inclusion in the system prompt.
 *
 * @property memoryIndex The index of stored memories to search
 * @property memorySearchService Service for searching memories by semantic similarity
 */
class MemoryContextAssembler(
    private val memoryIndex: MemoryIndex,
    private val memorySearchService: MemorySearchService
) {

    /**
     * Builds memory context string for the current conversation turn.
     *
     * Uses the last user message as the search query to find relevant memories.
     *
     * @param messages The current conversation messages
     * @param maxResults Maximum number of memory results to include (default: 5)
     * @return Formatted memory context string, or empty string if no user message found
     */
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
