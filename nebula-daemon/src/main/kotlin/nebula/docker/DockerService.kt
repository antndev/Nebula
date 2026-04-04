package nebula.docker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devnatan.dockerkt.DockerClient
import me.devnatan.dockerkt.models.PortBinding
import me.devnatan.dockerkt.models.container.exposedPort
import me.devnatan.dockerkt.models.container.hostConfig
import me.devnatan.dockerkt.models.portBindings
import me.devnatan.dockerkt.resource.container.create
import me.devnatan.dockerkt.resource.container.remove

data class CreateContainerRequest(
    val image: String,
    val command: List<String> = emptyList(),
    val containerPort: UShort? = null,
    val hostPort: UShort? = null,
    val bindIp: String = "0.0.0.0",
)

class DockerService(
    private val client: DockerClient,
) {
    companion object {
        fun connectForCurrentPlatform(): DockerService =
            DockerService(DockerClient { forCurrentPlatform() })
    }

    fun pullImage(image: String) = client.images.pull(image)

    suspend fun createContainer(request: CreateContainerRequest): String =
        withContext(Dispatchers.IO) {
            client.containers.create {
                image = request.image

                if (request.command.isNotEmpty()) {
                    command = request.command
                }

                request.containerPort?.let { containerPort ->
                    exposedPort(containerPort)

                    request.hostPort?.let { hostPort ->
                        hostConfig {
                            portBindings(containerPort) {
                                add(PortBinding(ip = request.bindIp, port = hostPort))
                            }
                        }
                    }
                }
            }
        }

    suspend fun startContainer(containerId: String) {
        withContext(Dispatchers.IO) {
            client.containers.start(containerId)
        }
    }

    suspend fun inspectContainer(containerId: String) =
        withContext(Dispatchers.IO) {
            client.containers.inspect(containerId)
        }

    suspend fun removeContainer(
        containerId: String,
        force: Boolean = true,
        removeAnonymousVolumes: Boolean = true,
    ) {
        withContext(Dispatchers.IO) {
            client.containers.remove(containerId) {
                this.force = force
                this.removeAnonymousVolumes = removeAnonymousVolumes
            }
        }
    }

    suspend fun createAndStartContainer(request: CreateContainerRequest): String {
        val containerId = createContainer(request)
        startContainer(containerId)
        return containerId
    }
}
