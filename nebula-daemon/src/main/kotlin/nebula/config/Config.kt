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
    val environment: Map<String, String> = emptyMap(),
    val scaling: ScalingBehavior = ScalingBehavior(),
) {
    init {
        require(name.isNotBlank()) { "name must not be blank." }
        require(image.isNotBlank()) { "image must not be blank." }
        require(startPort in 1..65535) { "startPort must be between 1 and 65535." }
        require(endPort in 1..65535) { "endPort must be between 1 and 65535." }
        require(startPort <= endPort) { "startPort must be less than or equal to endPort." }
        require(environment.keys.all { it.isNotBlank() }) {
            "environment variable names must not be blank."
        }

        val portCapacity = endPort - startPort + 1
        require(scaling.minInstances <= portCapacity) {
            "minInstances (${scaling.minInstances}) cannot exceed port capacity ($portCapacity)."
        }
        require(scaling.maxInstances == null || scaling.maxInstances <= portCapacity) {
            "maxInstances (${scaling.maxInstances}) cannot exceed port capacity ($portCapacity)."
        }
    }

    val portCapacity: Int
        get() = endPort - startPort + 1

    val maxPossibleInstances: Int
        get() = portCapacity

    val effectiveMaxInstances: Int
        get() = scaling.maxInstances ?: maxPossibleInstances
}

data class ScalingBehavior(
    val minInstances: Int = 0,
    val maxInstances: Int? = null,
    val playersToScaleUp: Int = 80,
    val maxPlayersPerInstance: Int = 100,
    val warmReadyInstances: Int = 0,
    val scalingCooldownSeconds: Int = 30,
    val scaleDownEmptyAfterSeconds: Int = 180,
    val distributionStrategy: PlayerDistributionStrategy = PlayerDistributionStrategy.PACK_FIRST,
) {
    init {
        require(minInstances >= 0) { "minInstances must be at least 0." }
        require(maxInstances == null || maxInstances >= 1) { "maxInstances must be at least 1 when set." }
        require(maxInstances == null || minInstances <= maxInstances) {
            "minInstances cannot exceed maxInstances."
        }
        require(playersToScaleUp >= 1) { "playersToScaleUp must be at least 1." }
        require(maxPlayersPerInstance >= 1) { "maxPlayersPerInstance must be at least 1." }
        require(playersToScaleUp < maxPlayersPerInstance) {
            "playersToScaleUp should stay below maxPlayersPerInstance so the next instance can start early."
        }
        require(warmReadyInstances >= 0) { "warmReadyInstances must be at least 0." }
        require(scalingCooldownSeconds >= 0) { "scalingCooldownSeconds must be at least 0." }
        require(scaleDownEmptyAfterSeconds >= 0) {
            "scaleDownEmptyAfterSeconds must be at least 0."
        }
    }
}

enum class PlayerDistributionStrategy {
    PACK_FIRST,
}
