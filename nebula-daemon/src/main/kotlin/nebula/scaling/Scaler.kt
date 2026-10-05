package nebula.scaling

import nebula.config.Config
import nebula.config.Service
import nebula.docker.CreateContainerRequest
import nebula.docker.DockerService
import me.devnatan.dockerkt.resource.image.ImageNotFoundException
import nebula.service.ServiceInstance
import nebula.service.ServiceInstanceStatus
import nebula.service.ServiceRegistry
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val SERVICE_CONTAINER_PORT: UShort = 25565u
private const val STARTUP_TIMEOUT_MS = 120_000L
private const val DOCKER_HOST_GATEWAY = "host.docker.internal:host-gateway"

private const val LABEL_MANAGED = "nebula.managed"
private const val LABEL_SERVICE = "nebula.service"
private const val LABEL_PORT = "nebula.port"
private const val LABEL_TOKEN = "nebula.token"

class Scaler(
    private val config: Config,
    private val dockerService: DockerService,
    private val registry: ServiceRegistry,
) {
    private val logger = LoggerFactory.getLogger(Scaler::class.java)
    private val lastScaledAt = ConcurrentHashMap<String, Long>()

    suspend fun reattach() {
        val containers = dockerService.listManagedContainers()
        if (containers.isEmpty()) {
            logger.info("no existing managed containers found.")
            return
        }

        logger.info("reattaching {} existing managed container(s)...", containers.size)
        for (container in containers) {
            if (container.token.isBlank()) {
                logger.info(
                    "removing {} (container {}): created without a token.",
                    "${container.serviceName}:${container.hostPort}",
                    container.containerId.take(12),
                )
                dockerService.removeContainer(container.containerId)
                continue
            }
            registry.register(
                ServiceInstance(
                    serviceName = container.serviceName,
                    hostPort = container.hostPort,
                    containerId = container.containerId,
                    token = container.token,
                    status = ServiceInstanceStatus.STARTING,
                )
            )
            logger.info("reattached {} (container {}).", "${container.serviceName}:${container.hostPort}", container.containerId.take(12))
        }
    }

    suspend fun bootstrap() {
        logger.info("bootstrapping {} service(s)...", config.services.size)
        reconcileAllServices()
    }

    suspend fun reconcileAllServices() {
        removeDeadInstances()
        config.services.forEach { service ->
            runCatching { reconcileService(service) }
                .onFailure { logger.error("reconciling '{}' failed: {}", service.name, it.message) }
        }
    }

    private suspend fun removeDeadInstances() {
        val containers = dockerService.listManagedContainers()
        val alive = containers.map { it.containerId }.toSet()
        val unknown = containers.filter { registry.instanceByContainer(it.containerId) == null }
        val now = System.currentTimeMillis()

        for (instance in registry.snapshot().values.flatten()) {
            val reason = when {
                instance.containerId !in alive -> "container is gone"
                instance.status == ServiceInstanceStatus.STARTING && now - instance.statusSince > STARTUP_TIMEOUT_MS ->
                    "not connected for ${STARTUP_TIMEOUT_MS / 1000}s"
                else -> continue
            }
            logger.warn("removing {} (container {}): {}.", id(instance), instance.containerId.take(12), reason)
            removeInstance(instance)
        }

        for (container in unknown) {
            logger.warn(
                "removing unknown container {} ({}:{}).",
                container.containerId.take(12),
                container.serviceName,
                container.hostPort,
            )
            runCatching { dockerService.removeContainer(container.containerId) }
        }
    }

    private suspend fun reconcileService(service: Service) {
        val now = System.currentTimeMillis()
        val active = registry.getActiveInstances(service.name)
        val decision = ScalingPolicy.decide(service.scaling, active, now, lastScaledAt[service.name] ?: 0L)

        when (decision) {
            is ScalingDecision.Start -> {
                logger.info(
                    "scaling '{}' up: {} -> {} instances ({}).",
                    service.name,
                    active.size,
                    active.size + decision.count,
                    decision.reason,
                )
                lastScaledAt[service.name] = now
                repeat(decision.count) { createInstance(service) }
            }
            is ScalingDecision.Stop -> {
                logger.info("scaling '{}' down: stopping {} ({}).", service.name, id(decision.instance), decision.reason)
                lastScaledAt[service.name] = now
                removeInstance(decision.instance)
            }
            ScalingDecision.Keep -> Unit
        }
    }

    private suspend fun removeInstance(instance: ServiceInstance) {
        registry.markStopped(instance.hostPort)
        runCatching { dockerService.removeContainer(instance.containerId) }
            .onFailure { logger.debug("container {} was already gone: {}", instance.containerId.take(12), it.message) }
        registry.deregister(instance.serviceName, instance.containerId)
    }

    private fun id(instance: ServiceInstance): String = "${instance.serviceName}:${instance.hostPort}"

    private fun isImageMissing(e: Exception): Boolean =
        e is ImageNotFoundException || e.message?.contains("No such image", ignoreCase = true) == true

    private suspend fun pullImage(image: String) {
        logger.info("pulling image '{}'...", image)

        val layerStatus = mutableMapOf<String, String>()
        var upToDate = false

        dockerService.pullImage(image).collect { pull ->
            val status = pull.statusText

            if (pull.id == null) {
                if (status.contains("up to date", ignoreCase = true) ||
                    status.contains("Status:", ignoreCase = true)
                ) {
                    upToDate = status.contains("up to date", ignoreCase = true)
                    logger.info("  {}", status)
                }
                return@collect
            }

            val id = pull.id ?: return@collect
            val layerId = id.take(12)

            if (layerStatus[id] == status) return@collect
            layerStatus[id] = status

            when {
                status == "Pull complete" || status == "Already exists" || status == "Download complete" ->
                    logger.info("  [{}] {}", layerId, status)
                status == "Pulling fs layer" || status == "Waiting" ->
                    logger.debug("  [{}] {}", layerId, status)
            }
        }

        if (!upToDate) {
            logger.info("image '{}' ready.", image)
        }
    }

    private suspend fun createInstance(service: Service): ServiceInstance {
        val serviceMax = service.scaling.maxInstances
        check(serviceMax == null || registry.getActiveInstances(service.name).size < serviceMax) {
            "service '${service.name}' is already at its maximum instance count."
        }
        check(registry.totalActiveInstances() < config.maxInstancesPerNode) {
            "this node is already at its maximum of ${config.maxInstancesPerNode} instances."
        }

        val hostPort = registry.nextAvailablePort()
            ?: error("no free host ports remain in the node port range.")

        logger.info("creating {}.", "${service.name}:$hostPort")
        val token = UUID.randomUUID().toString()
        val request = CreateContainerRequest(
            image = service.image,
            containerPort = SERVICE_CONTAINER_PORT,
            hostPort = hostPort.toUShort(),
            labels = mapOf(
                LABEL_MANAGED to "true",
                LABEL_SERVICE to service.name,
                LABEL_PORT to hostPort.toString(),
                LABEL_TOKEN to token,
            ),
            env = service.environment + mapOf(
                "NEBULA_HOST" to config.managementHost,
                "NEBULA_PORT" to config.managementPort.toString(),
                "NEBULA_SERVICE_PORT" to hostPort.toString(),
                "NEBULA_TOKEN" to token,
            ),
            extraHosts = listOf(DOCKER_HOST_GATEWAY),
        )

        val containerId = try {
            dockerService.createAndStartContainer(request)
        } catch (e: Exception) {
            if (!isImageMissing(e)) throw e
            logger.info("image '{}' is not present locally.", service.image)
            pullImage(service.image)
            dockerService.createAndStartContainer(request)
        }

        val instance = ServiceInstance(
            serviceName = service.name,
            hostPort = hostPort,
            containerId = containerId,
            token = token,
            status = ServiceInstanceStatus.STARTING,
        )

        registry.register(instance)

        logger.info("created {} (container {}).", "${service.name}:$hostPort", containerId.take(12))

        return instance
    }
}
