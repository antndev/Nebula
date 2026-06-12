package nebula.sdk.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import nebula.protocol.NebulaPlayer
import nebula.protocol.ServiceMessage
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage

class NodeConnection(
    private val daemonHost: String,
    private val daemonPort: Int,
    private val servicePort: Int,
    private val playersProvider: () -> List<NebulaPlayer>,
    private val reconnectDelaySeconds: Long = 5,
) {
    private val logger = LoggerFactory.getLogger(NodeConnection::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbox = Channel<ServiceMessage>(Channel.UNLIMITED)

    fun start() {
        scope.launch {
            while (isActive) {
                runCatching { connectAndRun() }.onFailure { e ->
                    logger.warn("Connection to node failed: {}", e.message)
                }
                delay(reconnectDelaySeconds * 1_000)
            }
        }
    }

    fun playerJoined(player: NebulaPlayer) {
        outbox.trySend(ServiceMessage.PlayerJoined(player))
    }

    fun playerLeft(uuid: String) {
        outbox.trySend(ServiceMessage.PlayerLeft(uuid))
    }

    private suspend fun connectAndRun() {
        while (outbox.tryReceive().isSuccess) {
        }

        val closed = CompletableDeferred<Unit>()
        val socket = httpClient.newWebSocketBuilder()
            .buildAsync(URI.create("ws://$daemonHost:$daemonPort/"), listener(closed))
            .await()

        logger.info("Connected to node at {}:{}.", daemonHost, daemonPort)
        send(socket, ServiceMessage.Hello(servicePort, playersProvider()))

        val sender = scope.launch {
            for (message in outbox) {
                runCatching { send(socket, message) }.onFailure { return@launch }
            }
        }

        try {
            closed.await()
            logger.warn("Connection to node lost, reconnecting in {}s...", reconnectDelaySeconds)
        } finally {
            sender.cancelAndJoin()
        }
    }

    private suspend fun send(socket: WebSocket, message: ServiceMessage) {
        socket.sendText(json.encodeToString(ServiceMessage.serializer(), message), true).await()
    }

    private fun listener(closed: CompletableDeferred<Unit>) = object : WebSocket.Listener {
        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closed.complete(Unit)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            closed.complete(Unit)
        }
    }
}
