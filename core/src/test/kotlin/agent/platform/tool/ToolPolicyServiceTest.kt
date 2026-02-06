package agent.platform.tool

import agent.platform.tool.domain.AskMode
import agent.platform.tool.domain.ExecSecurity
import agent.platform.tool.domain.ExecToolConfig
import agent.platform.tool.domain.ToolPolicyService
import agent.platform.tool.domain.ToolsConfig

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ToolPolicyServiceTest {

    @Test
    fun `tool in allow list returns Allow`() {
        val config = ToolsConfig(
            allow = listOf("read", "write", "exec"),
            deny = emptyList()
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("read")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Allow)
    }

    @Test
    fun `tool in deny list returns Deny`() {
        val config = ToolsConfig(
            allow = listOf("read", "write"),
            deny = listOf("exec")
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("exec")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Deny)
        assertTrue((result as ToolPolicyService.PolicyDecision.Deny).reason.contains("deny list"))
    }

    @Test
    fun `deny takes precedence over allow`() {
        val config = ToolsConfig(
            allow = listOf("exec"),
            deny = listOf("exec")
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("exec")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Deny)
    }

    @Test
    fun `tool not in allow list with ON_MISS ask mode returns Ask`() {
        val config = ToolsConfig(
            allow = listOf("read"),
            deny = emptyList(),
            exec = ExecToolConfig(ask = AskMode.ON_MISS)
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("unknown_tool")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Ask)
        assertTrue((result as ToolPolicyService.PolicyDecision.Ask).reason.contains("not in allow list"))
    }

    @Test
    fun `tool not in allow list with OFF ask mode returns Deny`() {
        val config = ToolsConfig(
            allow = listOf("read"),
            deny = emptyList(),
            exec = ExecToolConfig(ask = AskMode.OFF)
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("unknown_tool")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Deny)
        assertTrue((result as ToolPolicyService.PolicyDecision.Deny).reason.contains("ask mode is OFF"))
    }

    @Test
    fun `tool not in allow list with ALWAYS ask mode returns Ask`() {
        val config = ToolsConfig(
            allow = listOf("read"),
            deny = emptyList(),
            exec = ExecToolConfig(ask = AskMode.ALWAYS)
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("unknown_tool")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Ask)
        assertTrue((result as ToolPolicyService.PolicyDecision.Ask).reason.contains("Ask mode is ALWAYS"))
    }

    @Test
    fun `exec tool with DENY security returns Deny`() {
        val config = ToolsConfig(
            allow = listOf("exec"),
            deny = emptyList(),
            exec = ExecToolConfig(security = ExecSecurity.DENY)
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("exec", isExecTool = true)
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Deny)
        assertTrue((result as ToolPolicyService.PolicyDecision.Deny).reason.contains("disabled"))
    }

    @Test
    fun `exec tool with FULL security returns Allow`() {
        val config = ToolsConfig(
            allow = listOf("exec"),
            deny = emptyList(),
            exec = ExecToolConfig(security = ExecSecurity.FULL)
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("exec", isExecTool = true)
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Allow)
    }

    @Test
    fun `exec tool with ALLOWLIST security and safe bin returns Allow`() {
        val config = ToolsConfig(
            allow = listOf("exec"),
            deny = emptyList(),
            exec = ExecToolConfig(
                security = ExecSecurity.ALLOWLIST,
                safeBins = listOf("jq", "grep")
            )
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("exec", isExecTool = true, execCommand = "jq '.' file.json")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Allow)
        assertTrue((result as ToolPolicyService.PolicyDecision.Allow).reason!!.contains("jq"))
    }

    @Test
    fun `exec tool with ALLOWLIST security and unsafe bin with ON_MISS returns Ask`() {
        val config = ToolsConfig(
            allow = listOf("exec"),
            deny = emptyList(),
            exec = ExecToolConfig(
                security = ExecSecurity.ALLOWLIST,
                safeBins = listOf("jq", "grep"),
                ask = AskMode.ON_MISS
            )
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("exec", isExecTool = true, execCommand = "rm -rf /")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Ask)
        assertTrue((result as ToolPolicyService.PolicyDecision.Ask).reason.contains("not in safe bins"))
    }

    @Test
    fun `exec tool with ALLOWLIST security and unsafe bin with OFF returns Deny`() {
        val config = ToolsConfig(
            allow = listOf("exec"),
            deny = emptyList(),
            exec = ExecToolConfig(
                security = ExecSecurity.ALLOWLIST,
                safeBins = listOf("jq"),
                ask = AskMode.OFF
            )
        )
        val service = ToolPolicyService(config)
        
        val result = service.evaluate("exec", isExecTool = true, execCommand = "rm -rf /")
        
        assertTrue(result is ToolPolicyService.PolicyDecision.Deny)
        assertTrue((result as ToolPolicyService.PolicyDecision.Deny).reason.contains("ask mode is OFF"))
    }

    @Test
    fun `isToolAllowed returns true for allowed tool`() {
        val config = ToolsConfig(
            allow = listOf("read", "write"),
            deny = emptyList()
        )
        val service = ToolPolicyService(config)
        
        assertTrue(service.isToolAllowed("read"))
    }

    @Test
    fun `isToolAllowed returns false for denied tool`() {
        val config = ToolsConfig(
            allow = listOf("read", "exec"),
            deny = listOf("exec")
        )
        val service = ToolPolicyService(config)
        
        assertFalse(service.isToolAllowed("exec"))
    }

    @Test
    fun `isToolAllowed returns false for unknown tool`() {
        val config = ToolsConfig(
            allow = listOf("read"),
            deny = emptyList()
        )
        val service = ToolPolicyService(config)
        
        assertFalse(service.isToolAllowed("unknown"))
    }

    @Test
    fun `isSafeBin returns true for safe binary`() {
        val config = ToolsConfig(
            exec = ExecToolConfig(safeBins = listOf("jq", "grep"))
        )
        val service = ToolPolicyService(config)
        
        assertTrue(service.isSafeBin("jq"))
        assertTrue(service.isSafeBin("grep"))
    }

    @Test
    fun `isSafeBin returns false for unsafe binary`() {
        val config = ToolsConfig(
            exec = ExecToolConfig(safeBins = listOf("jq", "grep"))
        )
        val service = ToolPolicyService(config)
        
        assertFalse(service.isSafeBin("rm"))
    }

    @Test
    fun `getSafeBins returns configured safe bins`() {
        val safeBins = listOf("jq", "grep", "cat")
        val config = ToolsConfig(
            exec = ExecToolConfig(safeBins = safeBins)
        )
        val service = ToolPolicyService(config)
        
        assertEquals(safeBins, service.getSafeBins())
    }
}
