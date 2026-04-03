package nebula.sdk.minestom

import org.slf4j.LoggerFactory

object NebulaSdk {
    private val logger = LoggerFactory.getLogger("NebulaSDK")
    private var initialized = false

    fun init() {
        if (initialized) {
            return
        }

        initialized = true
        logger.info("Nebula SDK loaded.")
    }
}