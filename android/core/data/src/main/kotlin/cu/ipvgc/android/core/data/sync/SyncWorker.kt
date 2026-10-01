package cu.ipvgc.android.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cu.ipvgc.android.core.data.CatalogRepository
import cu.ipvgc.android.core.data.CostSheetRepository
import cu.ipvgc.android.core.data.IpvValueRepository
import cu.ipvgc.android.core.data.RateRepository
import cu.ipvgc.android.core.data.db.ConflictEntity
import cu.ipvgc.android.core.data.db.IpvDatabase
import cu.ipvgc.android.core.data.db.SyncStateEntity
import cu.ipvgc.android.core.network.IpvApi
import cu.ipvgc.android.core.network.SyncMutationDto
import cu.ipvgc.android.core.network.SyncPushBody
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject

/**
 * Push de outbox **antes** que pull. Wi‑Fi para bootstrap (doc 9.6).
 * Conserva PENDING si el servidor pide RESYNC_REQUIRED (E-5).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: IpvApi,
    private val db: IpvDatabase,
    private val catalog: CatalogRepository,
    private val ipv: IpvValueRepository,
    private val sheets: CostSheetRepository,
    private val rates: RateRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deviceId = inputData.getString("device_id") ?: "00000000-0000-7000-8000-00000000d001"
        val pending = db.outbox().pending()
        if (pending.isNotEmpty()) {
            val body =
                SyncPushBody(
                    pending.map {
                        val payload = runCatching {
                            val o = JSONObject(it.payloadJson)
                            o.keys().asSequence().associateWith { k -> o.get(k).toString() }
                        }.getOrDefault(emptyMap())
                        SyncMutationDto(it.mutationId, it.seqNo, it.entityType, it.entityId, it.op, it.baseVersion, payload)
                    },
                )
            val res = runCatching { api.syncPush(deviceId, 1, body) }.getOrNull()
            res?.body()?.acks?.forEach { ack ->
                db.outbox().ack(ack.mutation_id, ack.status, ack.result_code)
                if (ack.status == "CONFLICT") {
                    db.conflicts().upsert(
                        ConflictEntity(
                            ack.mutation_id,
                            pending.first { it.mutationId == ack.mutation_id }.entityType,
                            pending.first { it.mutationId == ack.mutation_id }.entityId,
                            ack.server_state?.toString(),
                            pending.first { it.mutationId == ack.mutation_id }.payloadJson,
                            ack.result_code,
                        ),
                    )
                }
            }
        }
        val state = db.syncState().get() ?: SyncStateEntity()
        val pull = runCatching { api.syncChanges(state.cursor?.toLongOrNull() ?: 0L, state.serverEpoch ?: 1L) }.getOrNull()
        val status = pull?.body()?.status
        if (status == "RESYNC_REQUIRED") {
            val boot = api.syncBootstrap().body()
            db.syncState().upsert(
                state.copy(
                    serverEpoch = boot?.epoch,
                    cursor = "0",
                    lastPullAt = System.currentTimeMillis(),
                ),
            )
        } else if (status == "OK") {
            db.syncState().upsert(
                state.copy(
                    cursor = pull?.body()?.cursor?.toString(),
                    serverEpoch = pull?.body()?.epoch,
                    lastPullAt = System.currentTimeMillis(),
                    bootstrapDone = true,
                ),
            )
        }
        catalog.products()
        catalog.materials()
        ipv.list()
        sheets.list()
        rates.current()
        return Result.success()
    }
}
