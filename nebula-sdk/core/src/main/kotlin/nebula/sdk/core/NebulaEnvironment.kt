package nebula.sdk.core

/**
 * Reads Nebula-specific environment variables injected by the daemon into every managed container.
 */
object NebulaEnvironment {
    /** Host address of the Nebula daemon, reachable from inside the container. */
    val daemonHost: String = System.getenv("NEBULA_HOST") ?: "host.docker.internal"

    /** Management port the Nebula daemon is listening on. */
    val daemonPort: Int = System.getenv("NEBULA_PORT")?.toIntOrNull() ?: 7654

    /**
     * The host-side port this service instance is mapped to.
     * Used by the daemon to identify which ServiceInstance is reporting.
     */
    val servicePort: Int = System.getenv("NEBULA_SERVICE_PORT")?.toIntOrNull()
        ?: error("NEBULA_SERVICE_PORT is not set — is this service running outside of Nebula?")
}
