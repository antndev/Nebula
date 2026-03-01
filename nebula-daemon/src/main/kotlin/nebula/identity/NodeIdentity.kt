package nebula.identity

import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

object NodeIdentity {

    private val logger = LoggerFactory.getLogger(NodeIdentity::class.java)
    private val nodeIdFile = File("nebula-daemon/nebula-data/node.id")

    val nodeId: String by lazy { loadOrGenerate() }

    private fun loadOrGenerate(): String {
        if (nodeIdFile.exists()) {
            val id = nodeIdFile.readText().trim()
            logger.info("Loaded existing node ID: {}", id)
            return id
        }

        val id = UUID.randomUUID().toString()
        nodeIdFile.parentFile.mkdirs()
        nodeIdFile.writeText(id)
        logger.info("Generated new node ID: {}", id)
        return id
    }
}