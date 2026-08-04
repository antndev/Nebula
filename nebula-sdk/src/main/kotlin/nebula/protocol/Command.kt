package nebula.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Command {

    @Serializable
    @SerialName("kick")
    data class Kick(val uuid: String, val reason: String? = null) : Command() {
        init {
            require(uuid.isNotBlank()) { "uuid must not be blank." }
        }
    }

    @Serializable
    @SerialName("transfer")
    data class Transfer(
        val uuid: String,
        val host: String,
        val port: Int,
        val token: String,
    ) : Command() {
        init {
            require(uuid.isNotBlank()) { "uuid must not be blank." }
            require(host.isNotBlank()) { "host must not be blank." }
            require(port in 1..65535) { "port must be between 1 and 65535." }
            require(token.isNotBlank()) { "token must not be blank." }
        }
    }

    @Serializable
    @SerialName("expect_player")
    data class ExpectPlayer(
        val token: String,
        val profile: PlayerProfile,
        val expiresAt: Long,
    ) : Command() {
        init {
            require(token.isNotBlank()) { "token must not be blank." }
        }
    }
}
