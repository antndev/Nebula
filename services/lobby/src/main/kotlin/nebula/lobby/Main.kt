package nebula.lobby

import nebula.sdk.minestom.NebulaSdk
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.block.Block
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaLobby")

fun main() {
    val port = System.getenv("LOBBY_PORT")?.toIntOrNull() ?: 25566

    NebulaSdk.init()

    val server = MinecraftServer.init()
    val instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer()

    instanceContainer.setGenerator { unit ->
        unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK)
    }

    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instanceContainer
        event.player.respawnPoint = Pos(0.0, 42.0, 0.0)
    }

    server.start("0.0.0.0", port)
    logger.info("Lobby service started on port {}.", port)
}
