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
}
