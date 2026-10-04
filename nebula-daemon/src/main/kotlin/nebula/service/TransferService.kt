package nebula.service

import nebula.config.Config
import nebula.protocol.Command

private const val EXPECT_TTL_MS = 10_000L

class TransferService(
    private val config: Config,
    private val registry: ServiceRegistry,
    private val sendCommand: suspend (Int, Command) -> Boolean,
) {
    suspend fun prepareTransfer(uuid: String, target: ServiceInstance): Command.Transfer? {
        val expiresAt = System.currentTimeMillis() + EXPECT_TTL_MS
        if (!sendCommand(target.hostPort, Command.ExpectPlayer(uuid, expiresAt))) return null
        registry.playerExpected(target.hostPort, expiresAt)
        return Command.Transfer(uuid, config.entrypointEvaluationBehavior.transferHost, target.hostPort)
    }
}
