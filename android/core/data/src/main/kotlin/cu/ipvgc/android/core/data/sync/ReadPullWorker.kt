package cu.ipvgc.android.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cu.ipvgc.android.core.data.CatalogRepository
import cu.ipvgc.android.core.data.CostSheetRepository
import cu.ipvgc.android.core.data.IpvValueRepository
import cu.ipvgc.android.core.data.RateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fase 5: solo **pull** de lectura. El push/outbox es Fase 6.
 * WorkManager: Wi‑Fi por defecto para descargas grandes (doc 9.6).
 */
@HiltWorker
class ReadPullWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val catalog: CatalogRepository,
    private val ipv: IpvValueRepository,
    private val sheets: CostSheetRepository,
    private val rates: RateRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        catalog.products()
        catalog.materials()
        ipv.list()
        sheets.list()
        rates.current()
        return Result.success()
    }
}
