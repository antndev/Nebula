package nebula.protocol

import kotlinx.serialization.Serializable

@Serializable
data class PlayerProfile(
    val uuid: String,
    val username: String,
    val textures: String? = null,
    val textureSignature: String? = null,
    val roles: List<String> = emptyList(),
) {
    init {
        require(uuid.isNotBlank()) { "uuid must not be blank." }
        require(username.isNotBlank()) { "username must not be blank." }
    }
}
