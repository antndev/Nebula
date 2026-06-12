package nebula.config

data class Config(
    val entrypointEvaluationBehavior: EntrypointEvaluationBehavior,
    val services: List<Service>,
    val managementHost: String = "host.docker.internal",
    val managementPort: Int = 7654,
)
