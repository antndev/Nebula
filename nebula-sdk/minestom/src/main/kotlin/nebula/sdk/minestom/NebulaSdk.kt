package nebula.sdk.minestom

import nebula.protocol.NebulaPlayer
import nebula.sdk.core.NebulaEnvironment
import nebula.sdk.core.NodeConnection
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import org.slf4j.LoggerFactory

object NebulaSdk {
    private val logger = LoggerFactory.getLogger("NebulaSDK")
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        val connection = NodeConnection(
            daemonHost = NebulaEnvironment.daemonHost,
            daemonPort = NebulaEnvironment.daemonPort,
            servicePort = NebulaEnvironment.servicePort,
            playersProvider = {
                MinecraftServer.getConnectionManager().onlinePlayers.map { it.toNebulaPlayer() }
            },
        )

        val events = MinecraftServer.getGlobalEventHandler()
        events.addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.isFirstSpawn) {
                connection.playerJoined(event.player.toNebulaPlayer())
            }
        }
        events.addListener(PlayerDisconnectEvent::class.java) { event ->
            connection.playerLeft(event.player.uuid.toString())
        }

        connection.start()
        logger.info("Nebula SDK initialized.")
    }

    private fun Player.toNebulaPlayer(): NebulaPlayer =
        NebulaPlayer(uuid.toString(), username)
}
