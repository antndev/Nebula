package nebula.entrypoint

import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.common.TransferPacket

class Entrypoint {
    init {
        val server = MinecraftServer.init()
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()

        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = instance
        }

        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.sendPacket(TransferPacket("griefergames.net", 25565)) // test
        }

        server.start("0.0.0.0", 25565)
    }
}