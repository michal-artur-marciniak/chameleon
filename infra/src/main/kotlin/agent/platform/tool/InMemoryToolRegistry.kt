package agent.platform.tool

class InMemoryToolRegistry(
    private val tools: List<ToolDefinition> = emptyList()
) : ToolRegistry {
    private val byName = tools.associateBy { it.name }

    override fun list(): List<ToolDefinition> = tools

    override fun get(name: String): ToolDefinition? = byName[name]

    override suspend fun execute(call: ToolCallRequest): ToolResult {
        return ToolResult(
            content = "Tool execution not implemented for ${call.name}",
            isError = true
        )
    }
}
