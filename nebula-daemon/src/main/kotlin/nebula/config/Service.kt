package nebula.config

data class Service(
    val name: String,
    val image: String,
    val startPort: Int,
    val endPort: Int,
    /**
     * Whether each instance is tied to a stable identity (a "key") whose world must survive restarts.
     *
     * `false` (default) — pooled: instances are interchangeable, players are load-balanced across them,
     * and the pool scales with player count (lobbies, gamemodes).
     *
     * `true` — keyed: exactly one instance per key (e.g. a player's SMP), started on demand, its world
     * persisted across restarts, and never auto-deleted.
     */
    val persistent: Boolean = false,
    val joiningBehavior: JoiningBehavior = JoiningBehavior.FILL_EXISTING,
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
        require(!persistent || scaling.minInstances == 0) {
            "persistent services are started on demand per key; minInstances must be 0."
        }
    }

    val portCapacity: Int
        get() = endPort - startPort + 1

    val maxPossibleInstances: Int
        get() = portCapacity

    val effectiveMaxInstances: Int
        get() = scaling.maxInstances ?: maxPossibleInstances
}
