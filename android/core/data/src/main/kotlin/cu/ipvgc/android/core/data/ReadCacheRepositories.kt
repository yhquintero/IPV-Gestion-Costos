package cu.ipvgc.android.core.data

import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.data.db.CostSheetEntity
import cu.ipvgc.android.core.data.db.IpvControlEntity
import cu.ipvgc.android.core.data.db.IpvDatabase
import cu.ipvgc.android.core.data.db.IpvValueEntity
import cu.ipvgc.android.core.data.db.ProductEntity
import cu.ipvgc.android.core.data.db.RateEntity
import cu.ipvgc.android.core.data.db.RawMaterialEntity
import cu.ipvgc.android.core.network.IpvApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val api: IpvApi,
    private val db: IpvDatabase,
) {
    suspend fun products(forceNetwork: Boolean = true): Outcome<List<ProductEntity>> {
        if (forceNetwork) {
            val res = runCatching { api.products() }.getOrElse { return Outcome.Err(it.message ?: "network") }
            if (res.isSuccessful) {
                val now = System.currentTimeMillis()
                val rows = (res.body().orEmpty()).map {
                    ProductEntity(it.id, it.code, it.name, it.kind, lastSyncedAt = now)
                }
                db.products().upsertAll(rows)
            }
        }
        return Outcome.Ok(db.products().all())
    }

    suspend fun materials(forceNetwork: Boolean = true): Outcome<List<RawMaterialEntity>> {
        if (forceNetwork) {
            val res = runCatching { api.rawMaterials() }.getOrElse { return Outcome.Err(it.message ?: "network") }
            if (res.isSuccessful) {
                val now = System.currentTimeMillis()
                db.rawMaterials().upsertAll(
                    res.body().orEmpty().map { RawMaterialEntity(it.id, it.code, it.name, lastSyncedAt = now) },
                )
            }
        }
        return Outcome.Ok(db.rawMaterials().all())
    }
}

@Singleton
class IpvValueRepository @Inject constructor(
    private val api: IpvApi,
    private val db: IpvDatabase,
) {
    suspend fun list(forceNetwork: Boolean = true): Outcome<List<IpvValueEntity>> {
        if (forceNetwork) {
            val res = runCatching { api.ipvValues() }.getOrElse { return Outcome.Err(it.message ?: "network") }
            if (res.code() == 403) return Outcome.Err("Sin permiso costs:view", "forbidden")
            if (res.isSuccessful) {
                val now = System.currentTimeMillis()
                db.ipvValues().upsertAll(
                    res.body().orEmpty().map {
                        IpvValueEntity(it.id, it.currency, it.unit_price.toPlainString(), it.source_ref, lastSyncedAt = now)
                    },
                )
            }
        }
        return Outcome.Ok(db.ipvValues().all())
    }
}

@Singleton
class CostSheetRepository @Inject constructor(
    private val api: IpvApi,
    private val db: IpvDatabase,
) {
    suspend fun list(forceNetwork: Boolean = true): Outcome<List<CostSheetEntity>> {
        if (forceNetwork) {
            val res = runCatching { api.costSheets() }.getOrElse { return Outcome.Err(it.message ?: "network") }
            if (res.isSuccessful) {
                val now = System.currentTimeMillis()
                db.costSheets().upsertAll(
                    res.body().orEmpty().map {
                        CostSheetEntity(
                            id = it.id,
                            code = it.code,
                            status = it.current_version?.status,
                            totalCost = it.current_version?.total_cost?.toPlainString(),
                            versionId = it.current_version?.id,
                            lastSyncedAt = now,
                        )
                    },
                )
            }
        }
        return Outcome.Ok(db.costSheets().all())
    }
}

@Singleton
class ControlRepository @Inject constructor(
    private val api: IpvApi,
    private val db: IpvDatabase,
) {
    suspend fun list(forceNetwork: Boolean = true): Outcome<List<IpvControlEntity>> {
        if (forceNetwork) {
            val res = runCatching { api.controls() }.getOrElse { return Outcome.Err(it.message ?: "network") }
            if (res.isSuccessful) {
                val now = System.currentTimeMillis()
                db.controls().upsertAll(
                    res.body().orEmpty().map {
                        IpvControlEntity(it.id, it.control_no, it.status, it.mode, lastSyncedAt = now)
                    },
                )
            }
        }
        return Outcome.Ok(db.controls().all())
    }
}

@Singleton
class RateRepository @Inject constructor(
    private val api: IpvApi,
    private val db: IpvDatabase,
) {
    suspend fun current(forceNetwork: Boolean = true): Outcome<List<RateEntity>> {
        if (forceNetwork) {
            val res = runCatching { api.rates() }.getOrElse { return Outcome.Err(it.message ?: "network") }
            if (res.isSuccessful) {
                val now = System.currentTimeMillis()
                db.rates().upsertAll(
                    res.body().orEmpty().map {
                        RateEntity(it.instrument, it.value, it.status, it.source, it.isTest, it.label, now)
                    },
                )
            }
        }
        return Outcome.Ok(db.rates().all())
    }
}
