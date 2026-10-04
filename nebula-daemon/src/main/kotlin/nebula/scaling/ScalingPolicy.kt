package nebula.scaling

import nebula.config.ScalingBehavior
import nebula.service.ServiceInstance
import nebula.service.ServiceInstanceStatus

sealed interface ScalingDecision {
    data class Start(val count: Int, val reason: String) : ScalingDecision
    data class Stop(val instance: ServiceInstance, val reason: String) : ScalingDecision
    data object Keep : ScalingDecision
}

object ScalingPolicy {
    fun decide(
        scaling: ScalingBehavior,
        active: List<ServiceInstance>,
        now: Long,
        lastScaledAt: Long,
    ): ScalingDecision {
        if (active.size < scaling.minInstances) {
            return ScalingDecision.Start(scaling.minInstances - active.size, "below minimum")
        }

        val spare = active.count { it.connectedPlayers < scaling.playersToScaleUp }
        val cooledDown = now - lastScaledAt >= scaling.scalingCooldownSeconds * 1000L
        val belowMax = scaling.maxInstances == null || active.size < scaling.maxInstances

        if (spare < scaling.warmReadyInstances && belowMax) {
            if (!cooledDown) return ScalingDecision.Keep
            return ScalingDecision.Start(1, "only $spare instance(s) below ${scaling.playersToScaleUp} players")
        }

        val emptyAfterSeconds = scaling.scaleDownEmptyAfterSeconds ?: return ScalingDecision.Keep
        if (!cooledDown || active.size <= scaling.minInstances || spare - 1 < scaling.warmReadyInstances) {
            return ScalingDecision.Keep
        }

        val idle = active
            .filter {
                it.status == ServiceInstanceStatus.RUNNING &&
                    it.connectedPlayers == 0 &&
                    now - it.lastActiveAt >= emptyAfterSeconds * 1000L
            }
            .minByOrNull { it.lastActiveAt }
            ?: return ScalingDecision.Keep

        return ScalingDecision.Stop(idle, "empty for ${emptyAfterSeconds}s")
    }
}
