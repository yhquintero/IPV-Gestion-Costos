package cu.ipvgc.domain.sync

import java.util.UUID

data class OutboxItem(
    val mutation: PushItem,
    var state: OutboxState = OutboxState.PENDING,
    var attempts: Int = 0,
    var lastError: String? = null,
)

/**
 * Outbox del dispositivo: orden por [PushItem.seqNo], reintentos con tope, conserva PENDING en RESYNC.
 */
class ClientOutbox {
    private val items = linkedMapOf<UUID, OutboxItem>()
    private var nextSeq: Long = 1

    fun enqueue(
        entityType: SyncEntityType,
        entityId: UUID,
        op: MutationOp,
        payload: Map<String, String>,
        baseVersion: Long? = null,
        mutationId: UUID = UUID.randomUUID(),
    ): OutboxItem {
        val item =
            OutboxItem(
                PushItem(
                    mutationId = mutationId,
                    seqNo = nextSeq++,
                    entityType = entityType,
                    entityId = entityId,
                    op = op,
                    baseVersion = baseVersion,
                    payload = payload,
                ),
            )
        items[mutationId] = item
        return item
    }

    fun pending(): List<OutboxItem> = items.values.filter { it.state == OutboxState.PENDING }.sortedBy { it.mutation.seqNo }

    fun pendingCount(): Int = pending().size

    fun all(): Collection<OutboxItem> = items.values

    fun conflicts(): List<OutboxItem> = items.values.filter { it.state == OutboxState.CONFLICT }

    fun onAcks(acks: List<MutationAck>) {
        acks.forEach { ack ->
            val row = items[ack.mutationId] ?: return@forEach
            row.state =
                when (ack.status) {
                    MutationResultStatus.APPLIED -> OutboxState.APPLIED
                    MutationResultStatus.CONFLICT -> OutboxState.CONFLICT
                    MutationResultStatus.REJECTED -> OutboxState.REJECTED
                }
            row.lastError = ack.resultCode
        }
    }

    fun markAttempt(mutationId: UUID) {
        val row = items[mutationId] ?: return
        row.attempts += 1
        if (row.attempts >= SyncRules.MAX_ATTEMPTS && row.state == OutboxState.PENDING) {
            row.state = OutboxState.STUCK
        }
    }

    fun retainPendingOnResync() {
        items.values.filter { it.state == OutboxState.APPLIED || it.state == OutboxState.REJECTED }
        // PENDING y CONFLICT se conservan; APPLIED ya está en el servidor.
    }
}

data class LocalReplica(
    val rows: MutableMap<UUID, EntityRow> = mutableMapOf(),
) {
    fun apply(change: Change) {
        rows[change.entityId] =
            EntityRow(
                organizationId = change.organizationId,
                entityType = change.entityType,
                entityId = change.entityId,
                version = change.entityVersion,
                payload = change.payload,
                deleted = change.op == MutationOp.DELETE,
            )
    }
}

class SyncClient(
    val userId: UUID,
    val deviceId: UUID,
    private val processor: SyncProcessor,
    var epoch: Long = 1,
    var cursor: Long = 0,
) {
    val outbox = ClientOutbox()
    val replica = LocalReplica()

    fun cycle(): String {
        val pending = outbox.pending()
        pending.forEach { outbox.markAttempt(it.mutation.mutationId) }
        if (pending.isNotEmpty()) {
            val res = processor.push(PushRequest(userId, deviceId, items = pending.map { it.mutation }))
            outbox.onAcks(res.acks)
        }
        return when (val pull = processor.pull(userId, cursor, epoch)) {
            is PullResult.ResyncRequired -> {
                epoch = pull.epoch
                val boot = processor.bootstrap(userId)
                epoch = boot.first
                cursor = 0
                replica.rows.clear()
                outbox.retainPendingOnResync()
                when (val again = processor.pull(userId, cursor, epoch)) {
                    is PullResult.Changes -> {
                        again.changes.forEach { replica.apply(it) }
                        cursor = again.cursor
                        "RESYNC"
                    }
                    else -> "RESYNC"
                }
            }
            is PullResult.Changes -> {
                pull.changes.forEach { replica.apply(it) }
                cursor = pull.cursor
                epoch = pull.epoch
                "OK"
            }
            is PullResult.UpgradeRequired -> "UPGRADE"
        }
    }
}
