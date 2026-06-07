package nebula.sdk.minestom

import org.slf4j.LoggerFactory

object NebulaSdk {
    private val logger = LoggerFactory.getLogger("NebulaSDK")
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        // Telemetry/communication transport was removed — to be replaced with a live,
        // push-based channel (events on change, not a 10s poll).
        logger.info("Nebula SDK initialized.")
    }
}
