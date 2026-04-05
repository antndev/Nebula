package nebula.entrypoint

import nebula.config.EntrypointEvaluationBehavior
import nebula.entrypoint.model.ResolvedTransferTarget
import nebula.scaling.Scaler

class ContextEvaluator(
    private val config: EntrypointEvaluationBehavior,
    private val scaler: Scaler,
) {
    fun getTarget(): ResolvedTransferTarget {
        val serviceName = resolveTargetService()
        val instance = scaler.selectJoinTarget(serviceName)

        return ResolvedTransferTarget(
            host = config.transferHost,
            port = instance.hostPort,
            serviceName = serviceName,
            containerId = instance.containerId,
        )
    }
}
