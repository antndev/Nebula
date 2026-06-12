package nebula.sdk.core

object NebulaEnvironment {
    val daemonHost: String = System.getenv("NEBULA_HOST") ?: "host.docker.internal"

    val daemonPort: Int = System.getenv("NEBULA_PORT")?.toIntOrNull() ?: 7654

    val servicePort: Int = System.getenv("NEBULA_SERVICE_PORT")?.toIntOrNull()
        ?: error("NEBULA_SERVICE_PORT is not set — is this service running outside of Nebula?")
}
