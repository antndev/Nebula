package nebula.docker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devnatan.dockerkt.DockerClient
import me.devnatan.dockerkt.models.PortBinding
import me.devnatan.dockerkt.models.container.ContainerListOptions
import me.devnatan.dockerkt.models.container.exposedPort
import me.devnatan.dockerkt.models.container.hostConfig
import me.devnatan.dockerkt.models.portBindings
import me.devnatan.dockerkt.resource.container.create
import me.devnatan.dockerkt.resource.container.remove

data class CreateContainerRequest(
    val image: String,
    val containerPort: UShort? = null,
    val hostPort: UShort? = null,
    val labels: Map<String, String> = emptyMap(),
    val env: Map<String, String> = emptyMap(),
    val extraHosts: List<String> = emptyList(),
)

data class ManagedContainer(
    val containerId: String,
    val serviceName: String,
    val hostPort: Int,
    val token: String,
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

                if (request.labels.isNotEmpty()) {
                    labels = request.labels
                }

                if (request.env.isNotEmpty()) {
                    env = request.env.map { (k, v) -> "$k=$v" }
                }

                request.containerPort?.let { exposedPort(it) }

                hostConfig {
                    if (request.extraHosts.isNotEmpty()) {
                        extraHosts = request.extraHosts
                    }

                    val containerPort = request.containerPort
                    val hostPort = request.hostPort
                    if (containerPort != null && hostPort != null) {
                        portBindings(containerPort) {
                            add(PortBinding(ip = "0.0.0.0", port = hostPort))
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
        try {
            startContainer(containerId)
        } catch (e: Exception) {
            runCatching { removeContainer(containerId) }
            throw e
        }
        return containerId
    }

    suspend fun listManagedContainers(): List<ManagedContainer> =
        withContext(Dispatchers.IO) {
            client.containers.list(
                ContainerListOptions(
                    all = true,
                    filters = ContainerListOptions.Filters(label = listOf("nebula.managed=true")),
                )
            )
                .filter { it.state == "running" }
                .mapNotNull { container ->
                    val serviceName = container.labels["nebula.service"] ?: return@mapNotNull null
                    val hostPort = container.labels["nebula.port"]?.toIntOrNull() ?: return@mapNotNull null
                    val token = container.labels["nebula.token"].orEmpty()
                    ManagedContainer(container.id, serviceName, hostPort, token)
                }
        }
}
