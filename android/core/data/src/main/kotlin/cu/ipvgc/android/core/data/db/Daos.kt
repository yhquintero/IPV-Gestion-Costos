package cu.ipvgc.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY code")
    suspend fun all(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ProductEntity>)
}

@Dao
interface RawMaterialDao {
    @Query("SELECT * FROM raw_materials ORDER BY code")
    suspend fun all(): List<RawMaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<RawMaterialEntity>)
}

@Dao
interface IpvValueDao {
    @Query("SELECT * FROM ipv_values")
    suspend fun all(): List<IpvValueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<IpvValueEntity>)
}

@Dao
interface CostSheetDao {
    @Query("SELECT * FROM cost_sheets ORDER BY code")
    suspend fun all(): List<CostSheetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CostSheetEntity>)
}

@Dao
interface IpvControlDao {
    @Query("SELECT * FROM ipv_controls ORDER BY controlNo")
    suspend fun all(): List<IpvControlEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<IpvControlEntity>)
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates_cache ORDER BY instrument")
    suspend fun all(): List<RateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<RateEntity>)
}
