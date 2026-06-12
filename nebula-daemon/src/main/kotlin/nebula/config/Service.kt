package nebula.config

data class Service(
    val name: String,
    val image: String,
    val persistent: Boolean = false,
    val joiningBehavior: JoiningBehavior = JoiningBehavior.FILL_EXISTING,
    val environment: Map<String, String> = emptyMap(),
    val scaling: ScalingBehavior = ScalingBehavior(),
) {
    init {
        require(name.isNotBlank()) { "name must not be blank." }
        require(image.isNotBlank()) { "image must not be blank." }
        require(environment.keys.all { it.isNotBlank() }) {
            "environment variable names must not be blank."
        }
        require(!persistent || scaling.minInstances == 0) {
            "persistent services are started on demand per key; minInstances must be 0."
        }
    }
}
