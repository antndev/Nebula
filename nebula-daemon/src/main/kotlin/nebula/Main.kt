package nebula

import nebula.entrypoint.Entrypoint
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaD")



fun main() {
    logger.info("Starting the entrypoint...")
    Entrypoint()
    logger.info("Entrypoint started.")

}