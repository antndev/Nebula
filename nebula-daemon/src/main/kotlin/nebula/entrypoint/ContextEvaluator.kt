package nebula.entrypoint

import nebula.config.EntrypointEvaluationBehavior
import nebula.config.Service
import nebula.entrypoint.model.ResolvedTransferTarget
import nebula.service.ServiceRegistry

class ContextEvaluator(
    private val config: EntrypointEvaluationBehavior,
    private val services: List<Service>,
    private val registry: ServiceRegistry,
) {
    fun getTarget(): ResolvedTransferTarget {
        val serviceName = resolveTargetService()
        val service = services.firstOrNull { it.name == serviceName }
            ?: error("Unknown service '$serviceName'.")
        val instance = registry.selectJoinTarget(service)

        return ResolvedTransferTarget(
            host = config.transferHost,
            port = instance.hostPort,
            serviceName = serviceName,
            containerId = instance.containerId,
        )
    }

    // TODO: hostname-based routing (e.g. bedwars.example.com → bedwars-lobby)
    private fun resolveTargetService(): String = config.default
}
