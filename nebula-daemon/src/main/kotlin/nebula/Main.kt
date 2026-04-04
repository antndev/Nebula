package nebula

import nebula.config.Config
import nebula.config.EntrypointEvaluationBehavior
import nebula.config.ScalingBehavior
import nebula.config.Service
import nebula.entrypoint.Entrypoint
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaD")

fun main() {
    val config = Config(
        entrypointEvaluationBehavior = EntrypointEvaluationBehavior(default = "lobby"),
        services = listOf(
            Service(
                name = "lobby",
                image = "ghcr.io/antndev/nebula-lobby:latest",
                startPort = 25566,
                endPort = 25576,
                scaling = ScalingBehavior(
                    minInstances = 1,
                    maxInstances = 5,
                    playersToScaleUp = 80,
                    maxPlayersPerInstance = 100,
                    warmReadyInstances = 1,
                    scalingCooldownSeconds = 30,
                    scaleDownEmptyAfterSeconds = 180,
                ),
            )
        ),
    )

    logger.info("Starting the entrypoint...")
    Entrypoint(config)
    logger.info("Entrypoint started.")
}
