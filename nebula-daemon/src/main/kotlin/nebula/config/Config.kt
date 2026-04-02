package nebula.config

object ConfigHolder {
    lateinit var config: AppConfig
}

data class AppConfig(
    val entrypointEvaluationBehavior: EntrypointEvaluationBehavior,
    val services: List<Service>
)

data class EntrypointEvaluationBehavior(
    val default: String
)

data class Service(
    val name: String,
    val image: String,
    val startPort: Int,
    val endPort: Int,
)