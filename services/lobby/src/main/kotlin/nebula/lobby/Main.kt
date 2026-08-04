package nebula.lobby

import nebula.sdk.minestom.NebulaSdk
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaLobby")

fun main() {
    val port = System.getenv("LOBBY_PORT")?.toIntOrNull() ?: 25565
    System.setProperty("minestom.accept-transfers", "true")

    val server = MinecraftServer.init(Auth.Online())
    NebulaSdk.init()
    val instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer()
    instanceContainer.setChunkSupplier(::LightingChunk)

    instanceContainer.setGenerator { unit ->
        unit.modifier().fillHeight(0, 1, Block.GRASS_BLOCK)
    }

    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instanceContainer
        event.player.respawnPoint = Pos(0.0, 1.0, 0.0)
    }

    MinecraftServer.getGlobalEventHandler().addListener(PlayerBlockBreakEvent::class.java) { event ->
        event.isCancelled = true
    }

    server.start("0.0.0.0", port)
    logger.info("lobby service started on port {} and accepts transferred players.", port)
}
