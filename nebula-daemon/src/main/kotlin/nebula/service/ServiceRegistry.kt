package nebula.service

import nebula.config.JoiningBehavior
import nebula.config.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ServiceRegistry {
    private val instancesByService = ConcurrentHashMap<String, CopyOnWriteArrayList<ServiceInstance>>()

    fun register(instance: ServiceInstance) {
        instancesByService.computeIfAbsent(instance.serviceName) { CopyOnWriteArrayList() }.add(instance)
    }

    fun deregister(serviceName: String, containerId: String) {
        instancesByService[serviceName]?.removeIf { it.containerId == containerId }
    }

    fun getInstances(serviceName: String): List<ServiceInstance> =
        instancesByService[serviceName].orEmpty().toList()

    fun getActiveInstances(serviceName: String): List<ServiceInstance> =
        getInstances(serviceName).filter { it.status != ServiceInstanceStatus.STOPPED }

    fun nextAvailablePort(service: Service): Int? {
        val usedPorts = getActiveInstances(service.name).map { it.hostPort }.toSet()
        return (service.startPort..service.endPort).firstOrNull { it !in usedPorts }
    }

    fun selectJoinTarget(service: Service): ServiceInstance {
        val candidates = getJoinableInstances(service)
        return when (service.joiningBehavior) {
            JoiningBehavior.FILL_EXISTING -> candidates.maxByOrNull { it.connectedPlayers }
            JoiningBehavior.LEAST_PLAYERS -> candidates.minByOrNull { it.connectedPlayers }
        } ?: error("No joinable instances are available for service '${service.name}'.")
    }

    private fun getJoinableInstances(service: Service): List<ServiceInstance> {
        val ready = getInstances(service.name)
            .filter { it.status == ServiceInstanceStatus.READY && it.connectedPlayers < service.scaling.maxPlayersPerInstance }

        if (ready.isNotEmpty()) return ready

        // Fall back to STARTING instances while they are warming up (before first telemetry arrives).
        return getInstances(service.name)
            .filter { it.status == ServiceInstanceStatus.STARTING && it.connectedPlayers < service.scaling.maxPlayersPerInstance }
    }
}
