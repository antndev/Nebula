package nebula

import nebula.identity.NodeIdentity
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaD")

fun main() {
    logger.info("Starting NebulaD...")
    logger.info("Node ID: {}", NodeIdentity.nodeId)
    logger.info("NebulaD is ready.")
}