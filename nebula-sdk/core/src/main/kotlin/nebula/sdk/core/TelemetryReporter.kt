package nebula.sdk.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Periodically reports telemetry (player count, status) to the Nebula daemon via HTTP.
 *
 * Uses [java.net.http.HttpClient] — no extra dependencies required.
 *
 * @param daemonHost Host of the Nebula daemon, reachable from this process.
 * @param daemonPort Management port the daemon is listening on.
 * @param servicePort The host-side port this instance is mapped to (used as identity by the daemon).
 * @param playerCountProvider Called each interval to get the current player count.
 * @param intervalSeconds How often to report. Defaults to 10 seconds.
 */
class TelemetryReporter(
    private val daemonHost: String,
    private val daemonPort: Int,
    private val servicePort: Int,
    private val playerCountProvider: () -> Int,
    private val intervalSeconds: Long = 10,
) {
    private val logger = LoggerFactory.getLogger(TelemetryReporter::class.java)
    private val httpClient = HttpClient.newHttpClient()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            logger.info(
                "Telemetry reporter started — reporting to {}:{} every {}s.",
                daemonHost, daemonPort, intervalSeconds,
            )
            while (isActive) {
                sendReport()
                delay(intervalSeconds * 1_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun sendReport() {
        val playerCount = playerCountProvider()
        val body = """{"servicePort":$servicePort,"playerCount":$playerCount,"status":"READY"}"""

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://$daemonHost:$daemonPort/telemetry"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        runCatching {
            withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            }
        }.onFailure { e ->
            logger.warn("Failed to report telemetry to daemon: {}", e.message)
        }
    }
}
