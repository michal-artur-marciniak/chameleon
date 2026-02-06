package agent.platform.tool

import agent.platform.tool.domain.ToolDefinition
import agent.platform.tool.port.ToolDefinitionRegistry

/**
 * In-memory implementation of ToolDefinitionRegistry.
 */
class InMemoryToolDefinitionRegistry(
    private val tools: List<ToolDefinition> = emptyList()
) : ToolDefinitionRegistry {
    private val byName = tools.associateBy { it.name }

    override fun list(): List<ToolDefinition> = tools

    override fun get(name: String): ToolDefinition? = byName[name]

    override fun isRegistered(name: String): Boolean = name in byName
}
