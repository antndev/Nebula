package nebula.config

data class EntrypointEvaluationBehavior(
    val default: String,
    val transferHost: String = "127.0.0.1",
) {
    init {
        require(default.isNotBlank()) { "default must not be blank." }
        require(transferHost.isNotBlank()) { "transferHost must not be blank." }
    }
}
