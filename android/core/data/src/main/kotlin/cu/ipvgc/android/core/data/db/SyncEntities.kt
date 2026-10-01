package cu.ipvgc.android.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val mutationId: String,
    val seqNo: Long,
    val entityType: String,
    val entityId: String,
    val op: String,
    val baseVersion: Long?,
    val payloadJson: String,
    val state: String,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "conflicts")
data class ConflictEntity(
    @PrimaryKey val mutationId: String,
    val entityType: String,
    val entityId: String,
    val serverJson: String?,
    val clientJson: String?,
    val resultCode: String?,
)

@Entity(tableName = "inventory_counts")
data class InventoryCountEntity(
    @PrimaryKey val id: String,
    val businessDate: String,
    val observedQty: String,
    val itemCode: String,
    val syncState: String,
    val lastSyncedAt: Long? = null,
)
