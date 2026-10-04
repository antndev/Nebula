package nebula.entrypoint

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import nebula.config.Config
import nebula.service.ServiceRegistry
import nebula.service.TransferService
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.NetworkBuffer
import net.minestom.server.network.packet.PacketReading
import net.minestom.server.network.packet.PacketWriting
import net.minestom.server.network.packet.client.common.ClientPingRequestPacket
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket
import net.minestom.server.network.packet.server.ServerPacket
import net.minestom.server.network.packet.server.common.PingResponsePacket
import net.minestom.server.network.packet.server.common.TransferPacket
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket
import net.minestom.server.network.packet.server.login.LoginSuccessPacket
import net.minestom.server.network.packet.server.status.ResponsePacket
import net.minestom.server.network.player.GameProfile
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.EOFException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

private const val READ_TIMEOUT_MS = 10_000

class Entrypoint(
    private val config: Config,
    private val registry: ServiceRegistry,
    private val transferService: TransferService,
) {
    private val logger = LoggerFactory.getLogger(Entrypoint::class.java)
    private val contextEvaluator = ContextEvaluator(config.entrypointEvaluationBehavior, config.services, registry)

    init {
        val server = ServerSocket(Config.ENTRYPOINT_PORT)
        Thread.ofPlatform().name("entrypoint").start {
            while (true) {
                val socket = server.accept()
                Thread.ofVirtual().start { socket.use { handle(it) } }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = READ_TIMEOUT_MS
        socket.tcpNoDelay = true
        val input = DataInputStream(socket.getInputStream().buffered())
        val output = socket.getOutputStream()

        runCatching {
            val handshake = input.readPacket(ConnectionState.HANDSHAKE).read(ClientHandshakePacket.SERIALIZER)
            when (handshake.intent()) {
                ClientHandshakePacket.Intent.STATUS -> status(input, output)
                else -> login(handshake, input, output)
            }
        }.onFailure { e ->
            logger.debug("connection from {} closed: {}", socket.remoteSocketAddress, e.message)
        }
    }

    private fun status(input: DataInputStream, output: OutputStream) {
        input.readPacket(ConnectionState.STATUS)

        val online = registry.snapshot().values.sumOf { instances -> instances.sumOf { it.connectedPlayers } }
        val capacity = config.services.sumOf { service ->
            registry.getActiveInstances(service.name).size * service.scaling.maxPlayersPerInstance
        }
        val response = buildJsonObject {
            putJsonObject("version") {
                put("name", MinecraftServer.VERSION_NAME)
                put("protocol", MinecraftServer.PROTOCOL_VERSION)
            }
            putJsonObject("players") {
                put("max", capacity)
                put("online", online)
            }
            putJsonObject("description") {
                put("text", "Nebula")
            }
        }
        output.send(ConnectionState.STATUS, ResponsePacket(response.toString()))

        val ping = input.readPacket(ConnectionState.STATUS).read(ClientPingRequestPacket.SERIALIZER)
        output.send(ConnectionState.STATUS, PingResponsePacket(ping.number()))
    }

    private fun login(handshake: ClientHandshakePacket, input: DataInputStream, output: OutputStream) {
        if (handshake.protocolVersion() != MinecraftServer.PROTOCOL_VERSION) {
            output.disconnect("Please join with Minecraft ${MinecraftServer.VERSION_NAME}.")
            return
        }

        val start = input.readPacket(ConnectionState.LOGIN).read(ClientLoginStartPacket.SERIALIZER)
        val username = start.username()
        val uuid = start.profileId()

        val target = runCatching { contextEvaluator.getTarget() }.getOrElse { e ->
            logger.warn("failed to route {} ({}): {}", username, uuid, e.message)
            output.disconnect("No servers are available right now. Please try again later.")
            return
        }
        val transfer = runBlocking { transferService.prepareTransfer(uuid.toString(), target) }
        if (transfer == null) {
            output.disconnect("No servers are available right now. Please try again later.")
            return
        }

        output.send(ConnectionState.LOGIN, LoginSuccessPacket(GameProfile(uuid, username), UUID(0, 0)))
        input.readPacket(ConnectionState.LOGIN)
        output.send(ConnectionState.CONFIGURATION, TransferPacket(transfer.host, transfer.port))

        logger.info(
            "routed {} ({}) -> {} [{}].",
            username,
            uuid,
            "${target.serviceName}:${transfer.port}",
            target.containerId.take(12),
        )
        input.awaitClose()
    }

    private fun DataInputStream.readPacket(state: ConnectionState): NetworkBuffer {
        val length = readVarInt()
        require(length in 1..PacketReading.maxPacketSize(state)) { "invalid packet length $length." }
        val bytes = readNBytes(length)
        if (bytes.size < length) throw EOFException()
        return NetworkBuffer.wrap(bytes, 0, length).also { it.read(NetworkBuffer.VAR_INT) }
    }

    private fun DataInputStream.readVarInt(): Int {
        var value = 0
        repeat(5) { i ->
            val byte = readUnsignedByte()
            value = value or ((byte and 0x7F) shl (7 * i))
            if (byte and 0x80 == 0) return value
        }
        error("varint is too long.")
    }

    private fun DataInputStream.awaitClose() {
        transferTo(OutputStream.nullOutputStream())
    }

    private fun OutputStream.send(state: ConnectionState, packet: ServerPacket) {
        write(NetworkBuffer.makeArray { PacketWriting.writeFramedPacket(it, state, packet, 0) })
        flush()
    }

    private fun OutputStream.disconnect(reason: String) {
        send(ConnectionState.LOGIN, LoginDisconnectPacket(Component.text(reason)))
    }
}
