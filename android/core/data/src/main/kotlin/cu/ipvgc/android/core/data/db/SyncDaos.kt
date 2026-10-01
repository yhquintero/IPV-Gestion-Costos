package cu.ipvgc.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE state = 'PENDING' ORDER BY seqNo")
    suspend fun pending(): List<OutboxEntity>

    @Query("SELECT count(*) FROM outbox WHERE state = 'PENDING'")
    suspend fun pendingCount(): Int

    @Query("SELECT COALESCE(MAX(seqNo), 0) FROM outbox")
    suspend fun maxSeq(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: OutboxEntity)

    @Query("UPDATE outbox SET state = :state, lastError = :error, attempts = attempts + 1 WHERE mutationId = :id")
    suspend fun ack(id: String, state: String, error: String?)

    @Query("SELECT * FROM outbox WHERE state = 'CONFLICT'")
    suspend fun conflicts(): List<OutboxEntity>
}

@Dao
interface ConflictDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ConflictEntity)

    @Query("SELECT * FROM conflicts")
    suspend fun all(): List<ConflictEntity>
}

@Dao
interface InventoryCountDao {
    @Query("SELECT * FROM inventory_counts ORDER BY businessDate DESC")
    suspend fun all(): List<InventoryCountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: InventoryCountEntity)
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SyncStateEntity)
}
