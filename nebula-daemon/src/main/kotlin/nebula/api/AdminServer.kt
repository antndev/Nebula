package nebula.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.devnatan.dockerkt.resource.container.ContainerAlreadyStartedException
import me.devnatan.dockerkt.resource.container.ContainerAlreadyStoppedException
import nebula.docker.DockerService
import nebula.protocol.Command
import nebula.protocol.NebulaPlayer
import nebula.service.ServiceInstance
import nebula.service.ServiceInstanceStatus
import nebula.service.ServiceRegistry
import org.slf4j.LoggerFactory

@Serializable
data class StateDto(val services: List<ServiceDto>)

@Serializable
data class ServiceDto(val name: String, val instances: List<InstanceDto>)

@Serializable
data class InstanceDto(
    val hostPort: Int,
    val status: String,
    val containerId: String,
    val players: List<NebulaPlayer>,
)

class AdminServer(
    private val registry: ServiceRegistry,
    private val socketServer: ServiceSocketServer,
    private val dockerService: DockerService,
    private val port: Int = 8080,
) {
    private val logger = LoggerFactory.getLogger(AdminServer::class.java)
    private val json = Json { encodeDefaults = true }

    fun start() {
        val server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json() }
            install(SSE)
            routing {
                get("/api/state") {
                    call.respond(buildState())
                }
                sse("/api/stream") {
                    send(ServerSentEvent(data = stateJson()))
                    registry.changes.collect {
                        send(ServerSentEvent(data = stateJson()))
                    }
                }
                post("/api/players/{uuid}/kick") {
                    val uuid = call.parameters["uuid"]
                    if (uuid.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    val instance = registry.findPlayerInstance(uuid)
                    if (instance == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }
                    val sent = socketServer.sendCommand(instance.hostPort, Command.Kick(uuid))
                    call.respond(if (sent) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable)
                }
                post("/api/instances/{port}/start") {
                    val instance = instanceFor(call.parameters["port"]) ?: return@post call.respond(HttpStatusCode.NotFound)
                    val result = runCatching { dockerService.startContainer(instance.containerId) }
                    if (result.isSuccess || result.exceptionOrNull() is ContainerAlreadyStartedException) {
                        registry.setStatus(instance.hostPort, ServiceInstanceStatus.STARTING)
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
                post("/api/instances/{port}/stop") {
                    val instance = instanceFor(call.parameters["port"]) ?: return@post call.respond(HttpStatusCode.NotFound)
                    registry.setStatus(instance.hostPort, ServiceInstanceStatus.STOPPED)
                    val result = runCatching { dockerService.stopContainer(instance.containerId) }
                    if (result.isSuccess || result.exceptionOrNull() is ContainerAlreadyStoppedException) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        registry.setStatus(instance.hostPort, ServiceInstanceStatus.RUNNING)
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
                delete("/api/instances/{port}") {
                    val instance = instanceFor(call.parameters["port"]) ?: return@delete call.respond(HttpStatusCode.NotFound)
                    runCatching { dockerService.removeContainer(instance.containerId) }
                    registry.deregisterByPort(instance.hostPort)
                    call.respond(HttpStatusCode.OK)
                }
                staticResources("/", "dashboard")
            }
        }
        server.start(wait = false)
        logger.info("admin dashboard on http://localhost:{}.", port)
    }

    private fun stateJson(): String = json.encodeToString(StateDto.serializer(), buildState())

    private fun instanceFor(portParam: String?): ServiceInstance? =
        portParam?.toIntOrNull()?.let { registry.instanceByPort(it) }

    private fun buildState(): StateDto =
        StateDto(
            registry.snapshot().map { (name, instances) ->
                ServiceDto(
                    name = name,
                    instances = instances.map { instance ->
                        InstanceDto(
                            hostPort = instance.hostPort,
                            status = instance.status.name,
                            containerId = instance.containerId,
                            players = instance.players,
                        )
                    },
                )
            }
        )
}
