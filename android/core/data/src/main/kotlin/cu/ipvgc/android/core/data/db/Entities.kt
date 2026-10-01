package cu.ipvgc.android.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val kind: String?,
    val syncState: String = "SYNCED",
    val lastSyncedAt: Long? = null,
)

@Entity(tableName = "raw_materials")
data class RawMaterialEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val syncState: String = "SYNCED",
    val lastSyncedAt: Long? = null,
)

@Entity(tableName = "ipv_values")
data class IpvValueEntity(
    @PrimaryKey val id: String,
    val currency: String,
    val unitPrice: String,
    val sourceRef: String?,
    val syncState: String = "SYNCED",
    val lastSyncedAt: Long? = null,
)

@Entity(tableName = "cost_sheets")
data class CostSheetEntity(
    @PrimaryKey val id: String,
    val code: String,
    val status: String?,
    val totalCost: String?,
    val versionId: String?,
    val syncState: String = "SYNCED",
    val lastSyncedAt: Long? = null,
)

@Entity(tableName = "ipv_controls")
data class IpvControlEntity(
    @PrimaryKey val id: String,
    val controlNo: String?,
    val status: String?,
    val mode: String?,
    val syncState: String = "SYNCED",
    val lastSyncedAt: Long? = null,
)

@Entity(tableName = "rates_cache")
data class RateEntity(
    @PrimaryKey val instrument: String,
    val value: String,
    val status: String?,
    val source: String?,
    val isTest: Boolean,
    val label: String?,
    val lastSyncedAt: Long? = null,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val cursor: String? = null,
    val serverEpoch: Long? = null,
    val lastPullAt: Long? = null,
    val bootstrapDone: Boolean = false,
    val schemaVersion: Int = 1,
)
