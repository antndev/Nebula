package nebula.config

data class Config(
    val entrypointEvaluationBehavior: EntrypointEvaluationBehavior,
    val services: List<Service>,
    val maxInstancesPerNode: Int = 50,
    val managementHost: String = "host.docker.internal",
    val managementPort: Int = 7654,
) {
    companion object {
        const val ENTRYPOINT_PORT = 25565
        val NODE_PORT_RANGE = 32800..32899
    }

    init {
        require(maxInstancesPerNode in 3..NODE_PORT_RANGE.count()) {
            "maxInstancesPerNode must be between 3 and ${NODE_PORT_RANGE.count()}."
        }
    }
}
