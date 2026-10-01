package cu.ipvgc.server.sync

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import cu.ipvgc.domain.sync.Change
import cu.ipvgc.domain.sync.EntityRow
import cu.ipvgc.domain.sync.MutationAck
import cu.ipvgc.domain.sync.MutationOp
import cu.ipvgc.domain.sync.MutationResultStatus
import cu.ipvgc.domain.sync.PushItem
import cu.ipvgc.domain.sync.SyncEntityType
import cu.ipvgc.domain.sync.SyncStore
import cu.ipvgc.server.access.CurrentUser
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class JdbcSyncStore(
    private val jdbc: JdbcTemplate,
    private val user: CurrentUser,
) : SyncStore {
    private val json = jacksonObjectMapper()

    override fun findMutation(mutationId: UUID): MutationAck? =
        jdbc.query(
            """
            SELECT mutation_id, status, result_code, entity_version, result_payload, client_payload
              FROM sync_mutations WHERE mutation_id = ?
            """.trimIndent(),
            { rs, _ ->
                MutationAck(
                    mutationId = rs.getObject("mutation_id", UUID::class.java),
                    status = MutationResultStatus.valueOf(rs.getString("status")),
                    resultCode = rs.getString("result_code"),
                    entityVersion = rs.getLong("entity_version").takeIf { !rs.wasNull() },
                    serverState = rs.getString("result_payload")?.let { json.readValue(it) },
                    clientPayload = rs.getString("client_payload")?.let { json.readValue(it) },
                )
            },
            mutationId,
        ).firstOrNull()

    override fun saveMutation(ack: MutationAck, item: PushItem, organizationId: UUID, deviceId: UUID) {
        jdbc.update(
            """
            INSERT INTO sync_mutations (
                mutation_id, organization_id, device_id, seq_no, entity_type, entity_id, op,
                base_version, status, result_code, client_payload, result_payload, entity_version
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)
            ON CONFLICT (mutation_id) DO NOTHING
            """.trimIndent(),
            ack.mutationId, organizationId, deviceId, item.seqNo, item.entityType.name, item.entityId, item.op.name,
            item.baseVersion, ack.status.name, ack.resultCode,
            json.writeValueAsString(item.payload),
            json.writeValueAsString(ack.serverState ?: emptyMap<String, String>()),
            ack.entityVersion,
        )
    }

    override fun getEntity(organizationId: UUID, type: SyncEntityType, id: UUID): EntityRow? =
        jdbc.query(
            """
            SELECT organization_id, entity_type, entity_id, version, payload, deleted_at
              FROM sync_shadow_entities
             WHERE organization_id = ? AND entity_type = ? AND entity_id = ?
            """.trimIndent(),
            { rs, _ ->
                EntityRow(
                    organizationId = rs.getObject(1, UUID::class.java),
                    entityType = SyncEntityType.valueOf(rs.getString(2)),
                    entityId = rs.getObject(3, UUID::class.java),
                    version = rs.getLong(4),
                    payload = json.readValue(rs.getString(5)),
                    deleted = rs.getTimestamp(6) != null,
                )
            },
            organizationId, type.name, id,
        ).firstOrNull()

    override fun putEntity(row: EntityRow) {
        jdbc.update(
            """
            INSERT INTO sync_shadow_entities (organization_id, entity_type, entity_id, version, payload, deleted_at)
            VALUES (?,?,?,?,?::jsonb, CASE WHEN ? THEN clock_timestamp() ELSE NULL END)
            ON CONFLICT (organization_id, entity_type, entity_id)
            DO UPDATE SET version = EXCLUDED.version, payload = EXCLUDED.payload, deleted_at = EXCLUDED.deleted_at
            """.trimIndent(),
            row.organizationId, row.entityType.name, row.entityId, row.version,
            json.writeValueAsString(row.payload), row.deleted,
        )
    }

    override fun appendChange(change: Change): Change {
        val seq = jdbc.queryForObject(
            """
            INSERT INTO sync_change_log (organization_id, entity_type, entity_id, op, entity_version, payload)
            VALUES (?,?,?,?,?,?::jsonb) RETURNING seq
            """.trimIndent(),
            Long::class.java,
            change.organizationId, change.entityType.name, change.entityId, change.op.name,
            change.entityVersion, json.writeValueAsString(change.payload),
        ) ?: 0L
        return change.copy(seq = seq)
    }

    override fun changesAfter(organizationId: UUID, cursor: Long, limit: Int): List<Change> =
        jdbc.query(
            """
            SELECT seq, organization_id, entity_type, entity_id, op, entity_version, payload
              FROM sync_change_log
             WHERE organization_id = ? AND seq > ?
             ORDER BY seq LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                Change(
                    seq = rs.getLong(1),
                    organizationId = rs.getObject(2, UUID::class.java),
                    entityType = SyncEntityType.valueOf(rs.getString(3)),
                    entityId = rs.getObject(4, UUID::class.java),
                    op = MutationOp.valueOf(rs.getString(5)),
                    entityVersion = rs.getLong(6),
                    payload = json.readValue(rs.getString(7) ?: "{}"),
                )
            },
            organizationId, cursor, limit,
        )

    override fun lastSeq(organizationId: UUID): Long =
        jdbc.queryForObject(
            "SELECT coalesce(max(seq),0) FROM sync_change_log WHERE organization_id = ?",
            Long::class.java,
            organizationId,
        ) ?: 0L

    override fun epoch(organizationId: UUID): Long {
        jdbc.update(
            "INSERT INTO sync_server_state (organization_id) VALUES (?) ON CONFLICT DO NOTHING",
            organizationId,
        )
        return jdbc.queryForObject(
            "SELECT epoch FROM sync_server_state WHERE organization_id = ?",
            Long::class.java,
            organizationId,
        ) ?: 1L
    }

    override fun setEpoch(organizationId: UUID, epoch: Long) {
        jdbc.update("UPDATE sync_server_state SET epoch = ? WHERE organization_id = ?", epoch, organizationId)
    }

    override fun minRetainedSeq(organizationId: UUID): Long =
        jdbc.queryForObject(
            "SELECT coalesce(min_retained_seq,0) FROM sync_server_state WHERE organization_id = ?",
            Long::class.java,
            organizationId,
        ) ?: 0L

    override fun setMinRetainedSeq(organizationId: UUID, seq: Long) {
        jdbc.update("UPDATE sync_server_state SET min_retained_seq = ? WHERE organization_id = ?", seq, organizationId)
    }

    override fun organizationOf(userId: UUID): UUID = user.organizationId

    override fun hasPermission(userId: UUID, permission: String): Boolean = user.has(permission)

    override fun licenseOk(userId: UUID): Boolean = true
}
