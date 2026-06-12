package nebula.entrypoint

import nebula.config.EntrypointEvaluationBehavior
import nebula.config.Service
import nebula.service.ServiceInstance
import nebula.service.ServiceRegistry

class ContextEvaluator(
    private val config: EntrypointEvaluationBehavior,
    private val services: List<Service>,
    private val registry: ServiceRegistry,
) {
    fun getTarget(): ServiceInstance {
        val serviceName = resolveTargetService()
        val service = services.firstOrNull { it.name == serviceName }
            ?: error("Unknown service '$serviceName'.")
        return registry.selectJoinTarget(service)
    }

    private fun resolveTargetService(): String = config.default
}
