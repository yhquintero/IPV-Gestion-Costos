package cu.ipvgc.domain.sync

import java.util.UUID

interface SyncStore {
    fun findMutation(mutationId: UUID): MutationAck?
    fun saveMutation(ack: MutationAck, item: PushItem, organizationId: UUID, deviceId: UUID)

    fun getEntity(organizationId: UUID, type: SyncEntityType, id: UUID): EntityRow?
    fun putEntity(row: EntityRow)

    fun appendChange(change: Change): Change
    fun changesAfter(organizationId: UUID, cursor: Long, limit: Int): List<Change>
    fun lastSeq(organizationId: UUID): Long

    fun epoch(organizationId: UUID): Long
    fun setEpoch(organizationId: UUID, epoch: Long)
    fun minRetainedSeq(organizationId: UUID): Long
    fun setMinRetainedSeq(organizationId: UUID, seq: Long)

    fun organizationOf(userId: UUID): UUID
    fun hasPermission(userId: UUID, permission: String): Boolean
    fun licenseOk(userId: UUID): Boolean
}

class InMemorySyncStore : SyncStore {
    data class User(
        val id: UUID,
        val organizationId: UUID,
        val permissions: MutableSet<String>,
        var licenseOk: Boolean = true,
    )

    private val users = mutableMapOf<UUID, User>()
    private val mutations = linkedMapOf<UUID, MutationAck>()
    private val entities = mutableMapOf<Triple<UUID, SyncEntityType, UUID>, EntityRow>()
    private val log = mutableListOf<Change>()
    private val epochs = mutableMapOf<UUID, Long>()
    private val minSeq = mutableMapOf<UUID, Long>()
    private val seq = mutableMapOf<UUID, Long>()

    fun addUser(user: User) {
        users[user.id] = user
        epochs.putIfAbsent(user.organizationId, 1L)
        minSeq.putIfAbsent(user.organizationId, 0L)
        seq.putIfAbsent(user.organizationId, 0L)
    }

    fun user(id: UUID): User = users.getValue(id)

    fun snapshot(): Map<Triple<UUID, SyncEntityType, UUID>, EntityRow> = entities.toMap()

    /** Simula restauración: sube epoch y recorta el log (el cursor del cliente queda inválido). */
    fun restoreBackup(organizationId: UUID, keepLast: Int = 0) {
        val kept = if (keepLast <= 0) emptyList() else log.filter { it.organizationId == organizationId }.takeLast(keepLast)
        log.removeAll { it.organizationId == organizationId }
        log.addAll(kept)
        val newMin = kept.firstOrNull()?.seq?.minus(1) ?: 0L
        minSeq[organizationId] = newMin
        epochs[organizationId] = epoch(organizationId) + 1
        if (kept.isEmpty()) seq[organizationId] = 0L
    }

    override fun findMutation(mutationId: UUID): MutationAck? = mutations[mutationId]

    override fun saveMutation(ack: MutationAck, item: PushItem, organizationId: UUID, deviceId: UUID) {
        mutations[ack.mutationId] = ack
    }

    override fun getEntity(organizationId: UUID, type: SyncEntityType, id: UUID): EntityRow? =
        entities[Triple(organizationId, type, id)]

    override fun putEntity(row: EntityRow) {
        entities[Triple(row.organizationId, row.entityType, row.entityId)] = row
    }

    override fun appendChange(change: Change): Change {
        val n = (seq[change.organizationId] ?: 0L) + 1
        seq[change.organizationId] = n
        val stored = change.copy(seq = n)
        log += stored
        return stored
    }

    override fun changesAfter(organizationId: UUID, cursor: Long, limit: Int): List<Change> =
        log.filter { it.organizationId == organizationId && it.seq > cursor }.take(limit)

    override fun lastSeq(organizationId: UUID): Long = seq[organizationId] ?: 0L

    override fun epoch(organizationId: UUID): Long = epochs[organizationId] ?: 1L

    override fun setEpoch(organizationId: UUID, epoch: Long) {
        epochs[organizationId] = epoch
    }

    override fun minRetainedSeq(organizationId: UUID): Long = minSeq[organizationId] ?: 0L

    override fun setMinRetainedSeq(organizationId: UUID, seq: Long) {
        minSeq[organizationId] = seq
    }

    override fun organizationOf(userId: UUID): UUID = users.getValue(userId).organizationId

    override fun hasPermission(userId: UUID, permission: String): Boolean =
        permission in users.getValue(userId).permissions

    override fun licenseOk(userId: UUID): Boolean = users.getValue(userId).licenseOk
}
