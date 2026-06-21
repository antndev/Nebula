package nebula.service

import nebula.config.Config
import nebula.protocol.Command
import nebula.protocol.PlayerProfile
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private const val TOKEN_TTL_MS = 10_000L

class TransferService(
    private val config: Config,
    private val sendCommand: suspend (Int, Command) -> Boolean,
) {
    private val random = SecureRandom()
    private val identities = ConcurrentHashMap<String, PlayerProfile>()

    fun rememberIdentity(profile: PlayerProfile) {
        identities[profile.uuid] = profile
    }

    suspend fun prepareTransfer(uuid: String, target: ServiceInstance): Command.Transfer? {
        val profile = identities[uuid] ?: return null
        val token = newToken()
        val expiresAt = System.currentTimeMillis() + TOKEN_TTL_MS
        if (!sendCommand(target.hostPort, Command.ExpectPlayer(token, profile, expiresAt))) return null
        return Command.Transfer(uuid, config.entrypointEvaluationBehavior.transferHost, target.hostPort, token)
    }

    private fun newToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
