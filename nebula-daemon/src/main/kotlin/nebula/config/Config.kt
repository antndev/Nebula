package nebula.config

data class Config(
    val entrypointEvaluationBehavior: EntrypointEvaluationBehavior,
    val services: List<Service>,
)

data class EntrypointEvaluationBehavior(
    val default: String,
)

data class Service(
    val name: String,
    val image: String,
    val startPort: Int,
    val endPort: Int,
)