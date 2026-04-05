package nebula.entrypoint

import nebula.config.Config
import nebula.scaling.Scaler
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.common.TransferPacket
import org.slf4j.LoggerFactory

class Entrypoint(config: Config, scaler: Scaler) {
    private val logger = LoggerFactory.getLogger(Entrypoint::class.java)
    private val contextEvaluator = ContextEvaluator(config.entrypointEvaluationBehavior, scaler)

    init {
        val server = MinecraftServer.init()
        val instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer()

        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = instanceContainer
        }

        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) {
                return@addListener
            }

            val target = contextEvaluator.getTarget()
            logger.info(
                "Player '{}' ({}) joined the entrypoint and was routed to service '{}' at {}:{} [container={}].",
                event.player.username,
                event.player.uuid,
                target.serviceName,
                target.host,
                target.port,
                target.containerId.take(12),
            )
            event.player.sendPacket(TransferPacket(target.host, target.port))
        }

        server.start("0.0.0.0", 25565)
    }
}
