package nebula.api

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import nebula.config.Config
import nebula.protocol.Command
import nebula.protocol.ServiceMessage
import nebula.service.ServiceRegistry
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class ServiceSocketServer(
    private val config: Config,
    private val registry: ServiceRegistry,
) {
    private val logger = LoggerFactory.getLogger(ServiceSocketServer::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val sessions = ConcurrentHashMap<Int, DefaultWebSocketServerSession>()

    suspend fun sendCommand(servicePort: Int, command: Command): Boolean {
        val session = sessions[servicePort] ?: return false
        session.send(Frame.Text(json.encodeToString(Command.serializer(), command)))
        return true
    }

    private fun id(hostPort: Int): String =
        registry.instanceByPort(hostPort)?.let { "${it.serviceName}:$hostPort" } ?: "?:$hostPort"

    fun start() {
        val server = embeddedServer(CIO, port = config.managementPort) {
            install(WebSockets) {
                pingPeriod = 30.seconds
                timeout = 90.seconds
            }
            routing {
                webSocket("/") {
                    val session = this
                    var servicePort: Int? = null
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            when (val message = json.decodeFromString(ServiceMessage.serializer(), frame.readText())) {
                                is ServiceMessage.Hello -> {
                                    servicePort = message.servicePort
                                    sessions[message.servicePort] = session
                                    val known = registry.serviceConnected(message.servicePort, message.players)
                                    if (known) {
                                        val count = message.players.size
                                        if (count > 0) {
                                            logger.info("{} connected ({} players).", id(message.servicePort), count)
                                        } else {
                                            logger.info("{} connected.", id(message.servicePort))
                                        }
                                    } else {
                                        logger.warn("hello from unknown service port {}.", message.servicePort)
                                    }
                                }
                                is ServiceMessage.PlayerJoined -> servicePort?.let { port ->
                                    val previous = registry.findPlayerInstance(message.player.uuid)
                                    registry.playerJoined(port, message.player)
                                    if (previous != null && previous.hostPort != port) {
                                        logger.debug("{} moved {} -> {}.", message.player.username, id(previous.hostPort), id(port))
                                    } else {
                                        logger.info("{} joined {}.", message.player.username, id(port))
                                    }
                                }
                                is ServiceMessage.PlayerLeft -> servicePort?.let { port ->
                                    registry.playerLeft(port, message.uuid)
                                    val still = registry.findPlayerInstance(message.uuid)
                                    if (still != null) {
                                        logger.debug("{} left {} (still on {}).", message.uuid, id(port), id(still.hostPort))
                                    } else {
                                        logger.info("{} left {}.", message.uuid, id(port))
                                    }
                                }
                                is ServiceMessage.TransferRequest -> servicePort?.let { port ->
                                    logger.info("{} requests transfer of {} to '{}'.", id(port), message.uuid, message.targetService)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("service socket error: {}", e.message)
                    } finally {
                        servicePort?.let { port ->
                            if (sessions.remove(port, session)) {
                                registry.serviceDisconnected(port)
                                logger.info("{} disconnected: {}.", id(port), session.closeReason.await())
                            }
                        }
                    }
                }
            }
        }

        server.start(wait = false)
        logger.info("service socket listening on port {}.", config.managementPort)
    }
}
