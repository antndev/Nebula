package nebula.config

data class Config(
    val entrypointEvaluationBehavior: EntrypointEvaluationBehavior,
    val services: List<Service>,
    /**
     * Host address that managed containers can use to reach the daemon.
     * Use "host.docker.internal" for Docker Desktop (Windows/Mac).
     * Use "172.17.0.1" for Linux bridge networks.
     */
    val managementHost: String = "host.docker.internal",
    val managementPort: Int = 7654,
)
