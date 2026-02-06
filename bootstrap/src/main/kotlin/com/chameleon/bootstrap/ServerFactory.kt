package com.chameleon.bootstrap

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

/**
 * Factory for creating the Ktor HTTP server.
 *
 * Creates a Netty-based embedded server with:
 * - Health check endpoint at /health
 * - WebSocket support at /ws
 *
 * Used by the bootstrap process to provide the gateway interface
 * for external communication and monitoring.
 */
class ServerFactory {
    /**
     * Creates and configures the Ktor embedded server.
     *
     * @param host The host address to bind to
     * @param port The port number to listen on
     * @return Configured EmbeddedServer instance ready to start
     */
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
