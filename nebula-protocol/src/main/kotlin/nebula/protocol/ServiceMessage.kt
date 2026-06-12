package nebula.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ServiceMessage {

    @Serializable
    @SerialName("hello")
    data class Hello(
        val servicePort: Int,
        val players: List<NebulaPlayer> = emptyList(),
    ) : ServiceMessage() {
        init {
            require(servicePort in 1..65535) { "servicePort must be between 1 and 65535." }
        }
    }

    @Serializable
    @SerialName("player_joined")
    data class PlayerJoined(val player: NebulaPlayer) : ServiceMessage()

    @Serializable
    @SerialName("player_left")
    data class PlayerLeft(val uuid: String) : ServiceMessage() {
        init {
            require(uuid.isNotBlank()) { "uuid must not be blank." }
        }
    }
}
