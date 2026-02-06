package com.chameleon.agent.application

import com.chameleon.agent.ContextBundle
import com.chameleon.session.domain.Session
import com.chameleon.tool.port.ToolDefinitionRegistry

interface ContextAssembler {
    fun build(
        session: Session,
        tools: ToolDefinitionRegistry
    ): ContextBundle
}
