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
    ],
    version = 1,
    exportSchema = true,
)
abstract class IpvDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun rawMaterials(): RawMaterialDao
    abstract fun ipvValues(): IpvValueDao
    abstract fun costSheets(): CostSheetDao
    abstract fun controls(): IpvControlDao
    abstract fun rates(): RateDao
}
