package agent.platform.server

import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

class ServerFactory {
    fun create(host: String, port: Int): EmbeddedServer<*, *> {
        return embeddedServer(Netty, host = host, port = port) {
            install(WebSockets)
            routing {
                get("/health") { call.respondText("ok") }
                webSocket("/ws") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            send(Frame.Text(frame.readText()))
                        }
                    }
                }
            }
        }
    }
}
