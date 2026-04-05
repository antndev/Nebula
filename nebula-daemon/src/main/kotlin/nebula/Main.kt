package nebula

import kotlinx.coroutines.runBlocking
import nebula.config.Config
import nebula.config.EntrypointEvaluationBehavior
import nebula.config.JoiningBehavior
import nebula.config.ScalingBehavior
import nebula.config.Service
import nebula.docker.DockerService
import nebula.entrypoint.Entrypoint
import nebula.scaling.Scaler
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NebulaD")

fun main() = runBlocking {
    val config = Config(
        entrypointEvaluationBehavior = EntrypointEvaluationBehavior(
            default = "lobby",
            transferHost = "127.0.0.1",
        ),
        services = listOf(
            Service(
                name = "lobby",
                image = "ghcr.io/antndev/nebula-lobby:latest",
                startPort = 25566,
                endPort = 25576,
                joiningBehavior = JoiningBehavior.FILL_EXISTING,
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
    val dockerService = DockerService.connectForCurrentPlatform()
    val scaler = Scaler(config, dockerService)

    logger.info("Bootstrapping minimum service instances...")
    scaler.bootstrap()
    logger.info("Starting the entrypoint...")
    Entrypoint(config, scaler)
    logger.info("Entrypoint started.")
}
