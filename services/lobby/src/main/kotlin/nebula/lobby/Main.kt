package nebula.lobby

import nebula.sdk.minestom.NebulaSdk
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaLobby")

fun main() {
    val port = System.getenv("LOBBY_PORT")?.toIntOrNull() ?: 25566

    NebulaSdk.init()
    logger.info("Starting lobby service on port {}...", port)
    val server = MinecraftServer.init()
    val instance = MinecraftServer.getInstanceManager().createInstanceContainer()

    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instance
    }

    server.start("0.0.0.0", port)
    logger.info("Lobby service started.")
}
