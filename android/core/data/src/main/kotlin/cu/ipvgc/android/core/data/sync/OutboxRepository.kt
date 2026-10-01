package cu.ipvgc.android.core.data.sync

import cu.ipvgc.android.core.data.db.InventoryCountEntity
import cu.ipvgc.android.core.data.db.IpvDatabase
import cu.ipvgc.android.core.data.db.OutboxEntity
import cu.ipvgc.domain.sync.MutationOp
import cu.ipvgc.domain.sync.SyncEntityType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboxRepository @Inject constructor(private val db: IpvDatabase) {
    suspend fun enqueueCount(itemCode: String, observedQty: String, businessDate: String): String {
        val id = UUID.randomUUID().toString()
        val mutationId = UUID.randomUUID().toString()
        val seq = db.outbox().maxSeq() + 1
        val payload = """{"itemCode":"$itemCode","observed_qty":"$observedQty","business_date":"$businessDate"}"""
        db.outbox().insert(
            OutboxEntity(
                mutationId = mutationId,
                seqNo = seq,
                entityType = SyncEntityType.INVENTORY_COUNT.name,
                entityId = id,
                op = MutationOp.UPSERT.name,
                baseVersion = null,
                payloadJson = payload,
                state = "PENDING",
            ),
        )
        db.counts().upsert(
            InventoryCountEntity(id, businessDate, observedQty, itemCode, "PENDING"),
        )
        return mutationId
    }

    suspend fun pendingCount(): Int = db.outbox().pendingCount()

    suspend fun pending(): List<OutboxEntity> = db.outbox().pending()

    suspend fun counts(): List<InventoryCountEntity> = db.counts().all()

    suspend fun conflicts() = db.conflicts().all()
}
