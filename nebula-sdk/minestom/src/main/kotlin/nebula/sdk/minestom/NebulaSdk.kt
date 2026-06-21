package nebula.sdk.minestom

import nebula.protocol.Command
import nebula.protocol.NebulaPlayer
import nebula.sdk.core.NodeConnection
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.common.CookieStorePacket
import net.minestom.server.network.packet.server.common.TransferPacket
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object NebulaSdk {
    private val logger = LoggerFactory.getLogger("NebulaSDK")
    private var initialized = false
    private val expected = ConcurrentHashMap<String, Command.ExpectPlayer>()

    fun init() {
        if (initialized) return
        initialized = true

        val connection = NodeConnection(
            daemonHost = System.getenv("NEBULA_HOST") ?: "host.docker.internal",
            daemonPort = System.getenv("NEBULA_PORT")?.toIntOrNull() ?: 7654,
            servicePort = System.getenv("NEBULA_SERVICE_PORT")?.toIntOrNull()
                ?: error("NEBULA_SERVICE_PORT is not set — is this service running outside of Nebula?"),
            playersProvider = {
                MinecraftServer.getConnectionManager().onlinePlayers.map { it.toNebulaPlayer() }
            },
            onCommand = { command -> handle(command) },
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
        logger.info("nebula sdk initialized.")
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Kick -> {
                val player = MinecraftServer.getConnectionManager().onlinePlayers
                    .find { it.uuid.toString() == command.uuid }
                player?.kick(Component.text(command.reason ?: "Kicked by an administrator."))
            }
            is Command.ExpectPlayer -> {
                expected[command.token] = command
            }
            is Command.Transfer -> {
                val player = MinecraftServer.getConnectionManager().onlinePlayers
                    .find { it.uuid.toString() == command.uuid } ?: return
                player.sendPacket(CookieStorePacket("nebula:token", command.token.encodeToByteArray()))
                player.sendPacket(TransferPacket(command.host, command.port))
            }
        }
    }

    private fun Player.toNebulaPlayer(): NebulaPlayer =
        NebulaPlayer(uuid.toString(), username)
}
