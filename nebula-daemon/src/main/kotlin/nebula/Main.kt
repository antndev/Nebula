package nebula

import nebula.config.Config
import nebula.config.EntrypointEvaluationBehavior
import nebula.entrypoint.Entrypoint
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaD")

fun main() {
    val config = Config(
        entrypointEvaluationBehavior = EntrypointEvaluationBehavior(default = "lobby"),
        services = emptyList(),
    )

    logger.info("Starting the entrypoint...")
    Entrypoint(config)
    logger.info("Entrypoint started.")
}
