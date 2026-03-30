package nebula

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import me.devnatan.dockerkt.DockerClient
import me.devnatan.dockerkt.DockerResponseException
import me.devnatan.dockerkt.models.ExposedPort
import me.devnatan.dockerkt.models.PortBinding
import me.devnatan.dockerkt.models.container.exposedPort
import me.devnatan.dockerkt.models.container.hostConfig
import me.devnatan.dockerkt.models.portBindings
import me.devnatan.dockerkt.resource.container.create
import me.devnatan.dockerkt.resource.container.remove
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaD")

fun main() = runBlocking {
    // currently just testing stuff
    val client = DockerClient { forCurrentPlatform() }
    client.images.pull("busybox:latest").collect()

    val created = mutableListOf<String>()

    try {
        val autoId = client.containers.create {
            image = "busybox:latest"
            command = listOf("sh", "-c", "sleep 600")
            exposedPort(25565u)

            hostConfig {
                // Docker picks a free random host port for every exposed port.
                publishAllPorts = true
            }
        }

        client.containers.start(autoId)
        created += autoId

        val autoContainer = client.containers.inspect(autoId)
        val autoBinding = autoContainer.networkSettings.ports[ExposedPort.fromString("25565/tcp")]
            ?.firstOrNull()

        logger.info("Auto-assigned port: ${autoBinding?.ip}:${autoBinding?.port}")

        repeat(2) { index ->
            try {
                val id = client.containers.create {
                    image = "busybox:latest"
                    command = listOf("sh", "-c", "sleep 600")
                    exposedPort(25565u)

                    hostConfig {
                        portBindings(25565u) {
                            add(PortBinding(ip = "0.0.0.0", port = 40000u))
                        }
                    }
                }

                client.containers.start(id)
                created += id
                logger.info("Started collision test container #$index on host port 40000")
            } catch (e: DockerResponseException) {
                logger.info("Docker refused container #$index as expected: ${e.message}")
            }
        }
    } finally {
        created.forEach { id ->
            runCatching {
                client.containers.remove(id) {
                    force = true
                    removeAnonymousVolumes = true
                }
            }
        }
    }
}
