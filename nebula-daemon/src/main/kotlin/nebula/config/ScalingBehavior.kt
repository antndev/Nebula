package nebula.config

data class ScalingBehavior(
    val minInstances: Int = 0,
    val maxInstances: Int? = null,
    val playersToScaleUp: Int = 80,
    val maxPlayersPerInstance: Int = 100,
    val warmReadyInstances: Int = 0,
    val scalingCooldownSeconds: Int = 30,
    val scaleDownEmptyAfterSeconds: Int = 180,
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
