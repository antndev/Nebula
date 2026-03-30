package nebula

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import me.devnatan.dockerkt.DockerClient
import me.devnatan.dockerkt.models.PortBinding
import me.devnatan.dockerkt.models.container.exposedPort
import me.devnatan.dockerkt.models.container.hostConfig
import me.devnatan.dockerkt.models.portBindings
import me.devnatan.dockerkt.models.system.SystemVersion
import me.devnatan.dockerkt.resource.container.create
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaD")

fun main() = runBlocking {
    val client = DockerClient { forCurrentPlatform() }

    val version: SystemVersion = client.system.version()
    client.images.pull("busybox:latest").collect()
    val id = client.containers.create {
        image = "busybox:latest"
        exposedPort(80u)
        hostConfig {
            portBindings(80u) {
                add(PortBinding(ip = "0.0.0.0", port = 8080u))
            }
        }
    }

    client.containers.start(id)

    val container = client.containers.inspect(id)
    val ports = container.networkSettings.ports

    logger.info("Version : ${version.version} | API: ${version.apiVersion}")
    logger.info("Container ID : $id")
    logger.info("Image        : ${container.image}")
    logger.info("State        : ${container.state}")
    ports.forEach { (port, bindings) ->
        bindings?.forEach { b -> logger.info("Port $port -> ${b.ip}:${b.port}") }
    }
}