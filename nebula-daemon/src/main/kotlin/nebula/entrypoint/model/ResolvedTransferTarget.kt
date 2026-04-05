package nebula.entrypoint.model

data class ResolvedTransferTarget(
    val host: String,
    val port: Int,
    val serviceName: String,
    val containerId: String,
)
