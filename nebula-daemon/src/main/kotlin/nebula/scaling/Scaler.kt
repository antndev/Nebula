package nebula.scaling

import kotlinx.coroutines.flow.collect
import nebula.config.Config
import nebula.config.Service
import nebula.docker.CreateContainerRequest
import nebula.docker.DockerService
import nebula.service.ServiceInstance
import nebula.service.ServiceInstanceStatus
import nebula.service.ServiceRegistry
import org.slf4j.LoggerFactory

private const val SERVICE_CONTAINER_PORT: UShort = 25566u

private const val LABEL_MANAGED = "nebula.managed"
private const val LABEL_SERVICE = "nebula.service"
private const val LABEL_PORT = "nebula.port"

class Scaler(
    private val config: Config,
    private val dockerService: DockerService,
    private val registry: ServiceRegistry,
) {
    private val logger = LoggerFactory.getLogger(Scaler::class.java)

    suspend fun reattach() {
        val containers = dockerService.listManagedContainers()
        if (containers.isEmpty()) {
            logger.info("No existing managed containers found.")
            return
        }

        logger.info("Reattaching {} existing managed container(s)...", containers.size)
        for (container in containers) {
            registry.register(
                ServiceInstance(
                    serviceName = container.serviceName,
                    hostPort = container.hostPort,
                    containerId = container.containerId,
                    status = ServiceInstanceStatus.STARTING,
                )
            )
            logger.info(
                "Reattached container {} as service '{}' on port {}.",
                container.containerId.take(12),
                container.serviceName,
                container.hostPort,
            )
        }
    }

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

    private suspend fun ensureMinimumInstances(service: Service) {
        val targetInstances = service.scaling.minInstances
        val currentInstances = registry.getActiveInstances(service.name).size
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
        check(registry.getActiveInstances(service.name).size < service.effectiveMaxInstances) {
            "Service '${service.name}' is already at its maximum tracked instance count."
        }

        val hostPort = registry.nextAvailablePort(service)
            ?: error("No free host ports remain for service '${service.name}'.")

        logger.info("Creating service instance '{}' on host port {}.", service.name, hostPort)
        val containerId = dockerService.createAndStartContainer(
            CreateContainerRequest(
                image = service.image,
                containerPort = SERVICE_CONTAINER_PORT,
                hostPort = hostPort.toUShort(),
                labels = mapOf(
                    LABEL_MANAGED to "true",
                    LABEL_SERVICE to service.name,
                    LABEL_PORT to hostPort.toString(),
                ),
            )
        )

        val instance = ServiceInstance(
            serviceName = service.name,
            hostPort = hostPort,
            containerId = containerId,
            status = ServiceInstanceStatus.STARTING,
        )

        registry.register(instance)

        logger.info(
            "Created service instance '{}' as container {} on port {}.",
            service.name,
            containerId.take(12),
            hostPort,
        )

        return instance
    }
}
