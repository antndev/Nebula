package nebula.service

import nebula.config.Config
import nebula.config.JoiningBehavior
import nebula.config.Service
import nebula.protocol.NebulaPlayer
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

    fun serviceConnected(hostPort: Int, players: List<NebulaPlayer>): Boolean =
        update(hostPort) { it.copy(status = ServiceInstanceStatus.READY, players = players) }

    fun serviceDisconnected(hostPort: Int) {
        update(hostPort) { it.copy(status = ServiceInstanceStatus.STARTING, players = emptyList()) }
    }

    fun playerJoined(hostPort: Int, player: NebulaPlayer) {
        update(hostPort) { instance ->
            instance.copy(players = instance.players.filterNot { it.uuid == player.uuid } + player)
        }
    }

    fun playerLeft(hostPort: Int, uuid: String) {
        update(hostPort) { instance ->
            instance.copy(players = instance.players.filterNot { it.uuid == uuid })
        }
    }

    private fun update(hostPort: Int, transform: (ServiceInstance) -> ServiceInstance): Boolean {
        for (instances in instancesByService.values) {
            val index = instances.indexOfFirst { it.hostPort == hostPort }
            if (index != -1) {
                instances[index] = transform(instances[index])
                return true
            }
        }
        return false
    }

    fun getInstances(serviceName: String): List<ServiceInstance> =
        instancesByService[serviceName].orEmpty().toList()

    fun getActiveInstances(serviceName: String): List<ServiceInstance> =
        getInstances(serviceName).filter { it.status != ServiceInstanceStatus.STOPPED }

    fun findPlayerInstance(uuid: String): ServiceInstance? =
        instancesByService.values.flatten().find { instance ->
            instance.players.any { it.uuid == uuid }
        }

    fun totalActiveInstances(): Int =
        instancesByService.values.sumOf { instances ->
            instances.count { it.status != ServiceInstanceStatus.STOPPED }
        }

    fun nextAvailablePort(): Int? {
        val usedPorts = instancesByService.values.flatten()
            .filter { it.status != ServiceInstanceStatus.STOPPED }
            .map { it.hostPort }
            .toSet()
        return Config.NODE_PORT_RANGE.firstOrNull { it !in usedPorts }
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

        return getInstances(service.name)
            .filter { it.status == ServiceInstanceStatus.STARTING && it.connectedPlayers < service.scaling.maxPlayersPerInstance }
    }
}
