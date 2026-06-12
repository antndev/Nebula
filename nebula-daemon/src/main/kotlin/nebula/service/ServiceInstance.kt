package nebula.service

import nebula.protocol.NebulaPlayer

data class ServiceInstance(
    val serviceName: String,
    val hostPort: Int,
    val containerId: String,
    val status: ServiceInstanceStatus = ServiceInstanceStatus.STARTING,
    val players: List<NebulaPlayer> = emptyList(),
) {
    val connectedPlayers: Int
        get() = players.size
}
