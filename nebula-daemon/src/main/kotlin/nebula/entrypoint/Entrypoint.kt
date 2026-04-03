package nebula.entrypoint

import nebula.config.Config
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.network.packet.server.common.TransferPacket

class Entrypoint(config: Config) {
    private val contextEvaluator = ContextEvaluator(config.entrypointEvaluationBehavior)

    init {
        val server = MinecraftServer.init()
        val instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer()

        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = instanceContainer
            val target = contextEvaluator.getTarget()
            event.player.sendPacket(TransferPacket(target.first, target.second)) // test
        }

        server.start("0.0.0.0", 25565)
    }
}
