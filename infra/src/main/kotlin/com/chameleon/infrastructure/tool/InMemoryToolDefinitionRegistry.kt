package com.chameleon.infrastructure.tool

import com.chameleon.tool.domain.ToolDefinition
import com.chameleon.tool.port.ToolDefinitionRegistry

/**
 * In-memory implementation of [ToolDefinitionRegistry].
 *
 * Stores tool definitions in a simple list/map structure.
 * Immutable after construction.
 */
class InMemoryToolDefinitionRegistry(
    private val tools: List<ToolDefinition> = emptyList()
) : ToolDefinitionRegistry {
    private val byName = tools.associateBy { it.name }

    override fun list(): List<ToolDefinition> = tools

    override fun get(name: String): ToolDefinition? = byName[name]

    override fun isRegistered(name: String): Boolean = name in byName
}
