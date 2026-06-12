package nebula.protocol

import kotlinx.serialization.Serializable

@Serializable
data class NebulaPlayer(
    val uuid: String,
    val username: String,
) {
    init {
        require(uuid.isNotBlank()) { "uuid must not be blank." }
        require(username.isNotBlank()) { "username must not be blank." }
    }
}
