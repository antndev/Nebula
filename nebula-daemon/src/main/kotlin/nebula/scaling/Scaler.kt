package nebula.scaling

import kotlinx.coroutines.flow.collect
import nebula.config.Config
import nebula.config.JoiningBehavior
import nebula.config.Service
import nebula.docker.CreateContainerRequest
import nebula.docker.DockerService
import nebula.scaling.model.ServiceInstance
import nebula.scaling.model.ServiceInstanceStatus
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val SERVICE_CONTAINER_PORT: UShort = 25566u

class Scaler(
    private val config: Config,
    private val dockerService: DockerService,
) {
    private val logger = LoggerFactory.getLogger(Scaler::class.java)
    private val instancesByService = ConcurrentHashMap<String, CopyOnWriteArrayList<ServiceInstance>>()

    suspend fun bootstrap() {
        config.services.forEach { service ->
            ensureMinimumInstances(service)
        }
    }

    suspend fun reconcileAllServices() {
        config.services.forEach { service ->
            reconcileService(service)
        }
    }

    suspend fun reconcileService(service: Service) {
        ensureMinimumInstances(service)

        // TODO: use reported instance state and player counts to decide scale up/down.
        // For now we only guarantee the configured baseline instance count exists.
    }

    fun getInstances(serviceName: String): List<ServiceInstance> =
        instancesByService[serviceName].orEmpty().toList()

    fun selectJoinTarget(serviceName: String): ServiceInstance {
        val service = config.services.firstOrNull { it.name == serviceName }
            ?: error("Unknown service '$serviceName'.")
        val candidates = getJoinableInstances(service)

        return when (service.joiningBehavior) {
            JoiningBehavior.FILL_EXISTING ->
                candidates.maxByOrNull { it.connectedPlayers }
            JoiningBehavior.LEAST_PLAYERS ->
                candidates.minByOrNull { it.connectedPlayers }
        } ?: error("No joinable instances are available for service '${service.name}'.")
    }

    fun updateInstanceStatus(
        serviceName: String,
        containerId: String,
        status: ServiceInstanceStatus,
        connectedPlayers: Int,
    ) {
        val serviceInstances = instancesByService[serviceName] ?: return
        val index = serviceInstances.indexOfFirst { it.containerId == containerId }
        if (index == -1) return

        serviceInstances[index] = serviceInstances[index].copy(
            status = status,
            connectedPlayers = connectedPlayers,
        )
    }

    private suspend fun ensureMinimumInstances(service: Service) {
        val targetInstances = service.scaling.minInstances
        val currentInstances = getActiveInstanceCount(service)
        val missingInstances = targetInstances - currentInstances

        if (missingInstances <= 0) {
            return
        }

        logger.info(
            "Service '{}' is below its minimum instance count ({} < {}). Creating {} instance(s).",
            service.name,
            currentInstances,
            targetInstances,
            missingInstances,
        )

        logger.info("Ensuring image '{}' is available for service '{}'.", service.image, service.name)
        dockerService.pullImage(service.image).collect()

        repeat(missingInstances) {
            createInstance(service)
        }
    }

    private suspend fun createInstance(service: Service): ServiceInstance {
        val activeInstances = getActiveInstanceCount(service)
        check(activeInstances < service.effectiveMaxInstances) {
            "Service '${service.name}' is already at its maximum tracked instance count."
        }

        val hostPort = nextAvailablePort(service)
            ?: error("No free host ports remain for service '${service.name}'.")

        logger.info("Creating service instance '{}' on host port {}.", service.name, hostPort)
        val containerId = dockerService.createAndStartContainer(
            CreateContainerRequest(
                image = service.image,
                containerPort = SERVICE_CONTAINER_PORT,
                hostPort = hostPort.toUShort(),
            )
        )

        val instance = ServiceInstance(
            serviceName = service.name,
            hostPort = hostPort,
            containerId = containerId,
            status = ServiceInstanceStatus.STARTING,
        )

        instancesByService.computeIfAbsent(service.name) { CopyOnWriteArrayList() }.add(instance)

        logger.info(
            "Created service instance '{}' as container {} on port {}.",
            service.name,
            containerId.take(12),
            hostPort,
        )

        return instance
    }

    private fun getActiveInstanceCount(service: Service): Int =
        getInstances(service.name).count { it.status != ServiceInstanceStatus.STOPPED }

    private fun getJoinableInstances(service: Service): List<ServiceInstance> {
        val readyInstances = getInstances(service.name)
            .filter { instance ->
                instance.status == ServiceInstanceStatus.READY &&
                    instance.connectedPlayers < service.scaling.maxPlayersPerInstance
            }

        if (readyInstances.isNotEmpty()) {
            return readyInstances
        }

        // TODO: remove this fallback once service instances report readiness to the daemon.
        return getInstances(service.name)
            .filter { instance ->
                instance.status == ServiceInstanceStatus.STARTING &&
                    instance.connectedPlayers < service.scaling.maxPlayersPerInstance
            }
    }

    private fun nextAvailablePort(service: Service): Int? {
        val usedPorts = getInstances(service.name)
            .filter { it.status != ServiceInstanceStatus.STOPPED }
            .map { it.hostPort }
            .toSet()

        return (service.startPort..service.endPort).firstOrNull { port -> port !in usedPorts }
    }
}
