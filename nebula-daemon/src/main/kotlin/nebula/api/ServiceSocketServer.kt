package nebula.api

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import nebula.config.Config
import nebula.protocol.ServiceMessage
import nebula.service.ServiceRegistry
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

class ServiceSocketServer(
    private val config: Config,
    private val registry: ServiceRegistry,
) {
    private val logger = LoggerFactory.getLogger(ServiceSocketServer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun start() {
        val server = embeddedServer(CIO, port = config.managementPort) {
            install(WebSockets) {
                pingPeriod = 15.seconds
            }
            routing {
                webSocket("/") {
                    var servicePort: Int? = null
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            when (val message = json.decodeFromString(ServiceMessage.serializer(), frame.readText())) {
                                is ServiceMessage.Hello -> {
                                    servicePort = message.servicePort
                                    val known = registry.serviceConnected(message.servicePort, message.players)
                                    if (known) {
                                        logger.info(
                                            "Service instance on port {} connected ({} player(s) online).",
                                            message.servicePort,
                                            message.players.size,
                                        )
                                    } else {
                                        logger.warn("Hello from unknown service port {}.", message.servicePort)
                                    }
                                }
                                is ServiceMessage.PlayerJoined -> servicePort?.let { port ->
                                    registry.playerJoined(port, message.player)
                                    logger.info(
                                        "Player '{}' ({}) joined instance on port {}.",
                                        message.player.username,
                                        message.player.uuid,
                                        port,
                                    )
                                }
                                is ServiceMessage.PlayerLeft -> servicePort?.let { port ->
                                    registry.playerLeft(port, message.uuid)
                                    logger.info("Player {} left instance on port {}.", message.uuid, port)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Service socket error: {}", e.message)
                    } finally {
                        servicePort?.let { port ->
                            registry.serviceDisconnected(port)
                            logger.info("Service instance on port {} disconnected.", port)
                        }
                    }
                }
            }
        }

        server.start(wait = false)
        logger.info("Service socket listening on port {}.", config.managementPort)
    }
}
