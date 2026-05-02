package nebula.sdk.minestom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nebula.sdk.core.NebulaEnvironment
import nebula.sdk.core.TelemetryReporter
import net.minestom.server.MinecraftServer
import org.slf4j.LoggerFactory

object NebulaSdk {
    private val logger = LoggerFactory.getLogger("NebulaSDK")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        TelemetryReporter(
            daemonHost = NebulaEnvironment.daemonHost,
            daemonPort = NebulaEnvironment.daemonPort,
            servicePort = NebulaEnvironment.servicePort,
            playerCountProvider = { MinecraftServer.getConnectionManager().onlinePlayers.size },
        ).start(scope)

        logger.info("Nebula SDK initialized.")
    }
}
