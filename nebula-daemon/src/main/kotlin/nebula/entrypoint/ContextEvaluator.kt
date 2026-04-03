package nebula.entrypoint

import nebula.config.EntrypointEvaluationBehavior

class ContextEvaluator(
    private val config: EntrypointEvaluationBehavior,
) {
    fun getTarget(): Pair<String, Int> {
        config.default
        return Pair("Server", 0)
    }
}
