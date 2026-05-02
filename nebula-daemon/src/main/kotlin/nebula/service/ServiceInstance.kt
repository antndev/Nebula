package nebula.service

data class ServiceInstance(
    val serviceName: String,
    val hostPort: Int,
    val containerId: String,
    val status: ServiceInstanceStatus = ServiceInstanceStatus.STARTING,
    val connectedPlayers: Int = 0,
)
