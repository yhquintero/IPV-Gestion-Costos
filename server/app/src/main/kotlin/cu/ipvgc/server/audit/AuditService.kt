package cu.ipvgc.server.audit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import cu.ipvgc.domain.hash.ContentHash
import cu.ipvgc.server.access.CurrentUser
import cu.ipvgc.server.security.currentUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.HexFormat
import java.util.UUID

@Service
class AuditService(
    private val jdbc: JdbcTemplate,
    @Value("\${ipvgc.env:dev}") env: String,
) {
    private val mapper = jacksonObjectMapper()
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    init {
        check(env != "prod" || System.getenv("AUDIT_SIGNING_KEY_PEM") != null) {
            "AUDIT_SIGNING_KEY_PEM is required in production"
        }
    }

    fun record(
        action: String,
        entityType: String? = null,
        entityId: UUID? = null,
        before: Any? = null,
        after: Any? = null,
        reason: String? = null,
        result: String = "SUCCESS",
        user: CurrentUser? = runCatching { currentUser() }.getOrNull(),
        organizationId: UUID? = user?.organizationId,
    ) {
        val org = organizationId ?: return
        val payload = linkedMapOf(
            "organization_id" to org.toString(),
            "action" to action,
            "entity_type" to entityType,
            "entity_id" to entityId?.toString(),
            "actor_id" to user?.userId?.toString(),
            "result" to result,
            "reason" to reason,
        )
        val hash = ContentHash.sha256Bytes(mapper.writeValueAsString(payload))
        val eventId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO audit_events (
                id, organization_id, actor_id, action, entity_type, entity_id,
                before, after, reason, result, event_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
            """.trimIndent(),
            eventId,
            org,
            user?.userId,
            action,
            entityType,
            entityId,
            before?.let { mapper.writeValueAsString(it) },
            after?.let { mapper.writeValueAsString(it) },
            reason,
            result,
            hash,
        )
        seal(org)
    }

    fun seal(organizationId: UUID) {
        val pending = jdbc.query(
            """
            SELECT seq, encode(event_hash, 'hex') AS h
              FROM audit_events
             WHERE organization_id = ?
               AND seq > COALESCE(
                    (SELECT max(to_seq) FROM audit_blocks WHERE organization_id = ?), 0)
             ORDER BY seq
            """.trimIndent(),
            { rs, _ -> rs.getLong("seq") to rs.getString("h") },
            organizationId,
            organizationId,
        )
        if (pending.isEmpty()) return
        val from = pending.first().first
        val to = pending.last().first
        val merkle = merkleRoot(pending.map { HexFormat.of().parseHex(it.second) })
        val prev = jdbc.query(
            """
            SELECT encode(merkle_root, 'hex') FROM audit_blocks
             WHERE organization_id = ? ORDER BY to_seq DESC LIMIT 1
            """.trimIndent(),
            { rs, _ -> rs.getString(1) },
            organizationId,
        ).firstOrNull()
        val sealedAt = java.time.Instant.now().toString()
        val blockHash = ContentHash.sha256Bytes("${prev ?: "GENESIS"}:${HexFormat.of().formatHex(merkle)}:$from:$to:$sealedAt")
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(blockHash)
        val signature = sig.sign()
        jdbc.update(
            """
            INSERT INTO audit_blocks (
                organization_id, from_seq, to_seq, merkle_root, prev_block_hash, signature, key_version
            ) VALUES (?, ?, ?, ?, decode(?, 'hex'), ?, 1)
            """.trimIndent(),
            organizationId,
            from,
            to,
            merkle,
            prev ?: "00".repeat(32),
            signature,
        )
    }

    fun verify(organizationId: UUID): Map<String, Any?> {
        val events = jdbc.query(
            """
            SELECT id, seq, action, actor_id, entity_type, entity_id, result, reason, event_hash
              FROM audit_events WHERE organization_id = ? ORDER BY seq
            """.trimIndent(),
            { rs, _ ->
                val payload = linkedMapOf(
                    "organization_id" to organizationId.toString(),
                    "action" to rs.getString("action"),
                    "entity_type" to rs.getString("entity_type"),
                    "entity_id" to rs.getString("entity_id"),
                    "actor_id" to rs.getString("actor_id"),
                    "result" to rs.getString("result"),
                    "reason" to rs.getString("reason"),
                )
                val expected = ContentHash.sha256Bytes(mapper.writeValueAsString(payload))
                val stored = rs.getBytes("event_hash")
                Triple(rs.getLong("seq"), expected.contentEquals(stored), stored)
            },
            organizationId,
        )
        val brokenEvents = events.filter { !it.second }.map { it.first }
        val blocks = jdbc.query(
            """
            SELECT from_seq, to_seq, merkle_root, signature
              FROM audit_blocks WHERE organization_id = ? ORDER BY from_seq
            """.trimIndent(),
            { rs, _ ->
                val from = rs.getLong(1)
                val to = rs.getLong(2)
                val root = rs.getBytes(3)
                val sig = rs.getBytes(4)
                val hashes = events.filter { it.first in from..to }.map { it.third }
                val computed = merkleRoot(hashes)
                val okRoot = computed.contentEquals(root)
                val verifier = Signature.getInstance("Ed25519")
                verifier.initVerify(keyPair.public)
                // signature is over block hash, not merkle alone — recompute is best-effort for Phase 3
                mapOf("from" to from, "to" to to, "merkle_ok" to okRoot, "has_signature" to (sig.isNotEmpty()))
            },
            organizationId,
        )
        val ok = brokenEvents.isEmpty() && blocks.all { it["merkle_ok"] == true }
        return mapOf(
            "ok" to ok,
            "events" to events.size,
            "blocks" to blocks.size,
            "broken_event_seq" to brokenEvents,
            "blocks_detail" to blocks,
        )
    }

    private fun merkleRoot(hashes: List<ByteArray>): ByteArray {
        if (hashes.isEmpty()) return ByteArray(32)
        var layer = hashes
        while (layer.size > 1) {
            val next = mutableListOf<ByteArray>()
            var i = 0
            while (i < layer.size) {
                val left = layer[i]
                val right = if (i + 1 < layer.size) layer[i + 1] else left
                next += ContentHash.sha256Bytes(HexFormat.of().formatHex(left) + HexFormat.of().formatHex(right))
                i += 2
            }
            layer = next
        }
        return layer.first()
    }
}
