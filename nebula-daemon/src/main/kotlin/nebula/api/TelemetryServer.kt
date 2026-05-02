package nebula.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nebula.service.ServiceInstanceStatus
import nebula.service.ServiceRegistry
import org.slf4j.LoggerFactory

private const val MANAGEMENT_PORT = 7654

class TelemetryServer(private val registry: ServiceRegistry) {
    private val logger = LoggerFactory.getLogger(TelemetryServer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun start() {
        embeddedServer(CIO, port = MANAGEMENT_PORT, configure = {}) {
            routing {
                post("/telemetry") {
                    val body = call.receiveText()

                    val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }

                    val servicePort = obj["servicePort"]?.jsonPrimitive?.int ?: run {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    val playerCount = obj["playerCount"]?.jsonPrimitive?.int ?: 0

                    if (registry.getInstanceByPort(servicePort) == null) {
                        logger.warn("Received telemetry for unknown port {}.", servicePort)
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }

                    registry.updateTelemetry(servicePort, playerCount, ServiceInstanceStatus.READY)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }.start(wait = false)

        logger.info("Management API listening on port {}.", MANAGEMENT_PORT)
    }
}
