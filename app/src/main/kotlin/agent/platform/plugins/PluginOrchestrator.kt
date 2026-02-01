package agent.platform.plugins

import agent.platform.config.PlatformConfig
import agent.platform.persistence.SessionIndexStore
import agent.sdk.ChannelPort
import agent.sdk.OutboundMessage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths

class PluginOrchestrator(
    private val config: PlatformConfig
) {
    private val workspace: Path = Paths.get(config.agent.workspace)
    private val indexStore = SessionIndexStore(workspace)

    fun startPlugin(plugin: ChannelPort) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            println("[${plugin.id}] coroutine error: ${throwable.message}")
        }
        CoroutineScope(Dispatchers.IO + handler).launch {
            println("[${plugin.id}] starting")
            plugin.start { inbound ->
                val sessionKey = buildSessionKey(
                    config.agent.id,
                    inbound.channelId,
                    inbound.chatId,
                    inbound.isGroup
                )
                indexStore.touchSession(sessionKey)
                println(
                    "[${plugin.id}] message chat=${inbound.chatId} user=${inbound.userId} " +
                        "group=${inbound.isGroup} mentioned=${inbound.isMentioned}"
                )
                plugin.send(
                    OutboundMessage(
                        channelId = inbound.channelId,
                        chatId = inbound.chatId,
                        text = inbound.text
                    )
                ).onFailure { error ->
                    println("[${plugin.id}] send failed: ${error.message}")
                }
            }
        }
    }

    private fun buildSessionKey(
        agentId: String,
        channelId: String,
        chatId: String,
        isGroup: Boolean
    ): String {
        return if (isGroup) {
            "agent:$agentId:$channelId:group:$chatId"
        } else {
            "agent:$agentId:main"
        }
    }
}
