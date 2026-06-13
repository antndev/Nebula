package nebula.service

import nebula.config.Config
import nebula.config.JoiningBehavior
import nebula.config.Service
import nebula.protocol.NebulaPlayer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ServiceRegistry {
    private val instancesByService = ConcurrentHashMap<String, CopyOnWriteArrayList<ServiceInstance>>()

    fun register(instance: ServiceInstance) {
        instancesByService.values.forEach { it.removeIf { existing -> existing.hostPort == instance.hostPort } }
        instancesByService.computeIfAbsent(instance.serviceName) { CopyOnWriteArrayList() }.add(instance)
    }

    fun deregister(serviceName: String, containerId: String) {
        instancesByService[serviceName]?.removeIf { it.containerId == containerId }
    }

    fun serviceConnected(hostPort: Int, players: List<NebulaPlayer>): Boolean =
        update(hostPort) { it.copy(status = ServiceInstanceStatus.RUNNING, players = players) }

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

    fun snapshot(): Map<String, List<ServiceInstance>> =
        instancesByService.mapValues { it.value.toList() }

    fun getInstances(serviceName: String): List<ServiceInstance> =
        instancesByService[serviceName].orEmpty().toList()

    fun getActiveInstances(serviceName: String): List<ServiceInstance> =
        getInstances(serviceName).filter { it.status != ServiceInstanceStatus.STOPPED }

    fun findPlayerInstance(uuid: String): ServiceInstance? =
        instancesByService.values.flatten().find { instance ->
            instance.players.any { it.uuid == uuid }
        }

    fun instanceByPort(hostPort: Int): ServiceInstance? =
        instancesByService.values.flatten().find { it.hostPort == hostPort }

    fun setStatus(hostPort: Int, status: ServiceInstanceStatus) {
        update(hostPort) { it.copy(status = status) }
    }

    fun deregisterByPort(hostPort: Int) {
        instancesByService.values.forEach { it.removeIf { instance -> instance.hostPort == hostPort } }
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
        return Config.NODE_PORT_RANGE.firstOrNull { it !in usedPorts && isPortFree(it) }
    }

    private fun isPortFree(port: Int): Boolean =
        runCatching { ServerSocket().use { it.bind(InetSocketAddress(port)) } }.isSuccess

    fun selectJoinTarget(service: Service): ServiceInstance {
        val candidates = getJoinableInstances(service)
        return when (service.joiningBehavior) {
            JoiningBehavior.FILL_EXISTING -> candidates.maxByOrNull { it.connectedPlayers }
            JoiningBehavior.LEAST_PLAYERS -> candidates.minByOrNull { it.connectedPlayers }
        } ?: error("no joinable instances are available for service '${service.name}'.")
    }

    private fun getJoinableInstances(service: Service): List<ServiceInstance> {
        val ready = getInstances(service.name)
            .filter { it.status == ServiceInstanceStatus.RUNNING && it.connectedPlayers < service.scaling.maxPlayersPerInstance }

        if (ready.isNotEmpty()) return ready

        return getInstances(service.name)
            .filter { it.status == ServiceInstanceStatus.STARTING && it.connectedPlayers < service.scaling.maxPlayersPerInstance }
    }
}
