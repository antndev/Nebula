package nebula.entrypoint

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nebula.config.Config
import nebula.protocol.PlayerProfile
import nebula.service.ServiceRegistry
import nebula.service.TransferService
import net.kyori.adventure.text.Component
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.common.CookieStorePacket
import net.minestom.server.network.packet.server.common.TransferPacket
import org.slf4j.LoggerFactory

class Entrypoint(config: Config, registry: ServiceRegistry, private val transferService: TransferService) {
    private val logger = LoggerFactory.getLogger(Entrypoint::class.java)
    private val contextEvaluator = ContextEvaluator(config.entrypointEvaluationBehavior, config.services, registry)
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        val server = MinecraftServer.init(Auth.Online())
        val instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer()

        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = instanceContainer
        }

        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) {
                return@addListener
            }
            val player = event.player
            val target = runCatching { contextEvaluator.getTarget() }.getOrElse { e ->
                logger.error("failed to route player '{}' ({})", player.username, player.uuid, e)
                player.kick(Component.text("No servers are available right now. Please try again later."))
                return@addListener
            }
            transferService.rememberIdentity(PlayerProfile(player.uuid.toString(), player.username))
            scope.launch {
                val transfer = transferService.prepareTransfer(player.uuid.toString(), target)
                if (transfer == null) {
                    player.kick(Component.text("No servers are available right now. Please try again later."))
                    return@launch
                }
                logger.info(
                    "player '{}' ({}) routed to '{}' at {}:{} [container={}].",
                    player.username,
                    player.uuid,
                    target.serviceName,
                    transfer.host,
                    transfer.port,
                    target.containerId.take(12),
                )
                player.sendPacket(CookieStorePacket("nebula:token", transfer.token.encodeToByteArray()))
                player.sendPacket(TransferPacket(transfer.host, transfer.port))
            }
        }

        server.start("0.0.0.0", Config.ENTRYPOINT_PORT)
    }
}
