package cu.ipvgc.android.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class,
        RawMaterialEntity::class,
        IpvValueEntity::class,
        CostSheetEntity::class,
        IpvControlEntity::class,
        RateEntity::class,
        SyncStateEntity::class,
        OutboxEntity::class,
        ConflictEntity::class,
        InventoryCountEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class IpvDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun rawMaterials(): RawMaterialDao
    abstract fun ipvValues(): IpvValueDao
    abstract fun costSheets(): CostSheetDao
    abstract fun controls(): IpvControlDao
    abstract fun rates(): RateDao
    abstract fun outbox(): OutboxDao
    abstract fun conflicts(): ConflictDao
    abstract fun counts(): InventoryCountDao
    abstract fun syncState(): SyncStateDao
}
