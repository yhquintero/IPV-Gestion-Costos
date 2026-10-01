package cu.ipvgc.android.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cu.ipvgc.android.core.data.db.IpvDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Versión 1: crea el esquema de caché de lectura. No hay migración 1→2 aún;
 * la prueba abre v1 y verifica tablas. SQLCipher se sustituye por SQLite
 * de instrumentación (la clave vive en Keystore, no en el APK).
 */
@RunWith(AndroidJUnit4::class)
class IpvDatabaseMigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            IpvDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun version1CreatesReadCacheTables() {
        helper.createDatabase(dbName, 2).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                val names = mutableSetOf<String>()
                while (c.moveToNext()) names.add(c.getString(0))
                require("products" in names)
                require("ipv_values" in names)
                require("cost_sheets" in names)
                require("ipv_controls" in names)
                require("rates_cache" in names)
                require("sync_state" in names)
                require("outbox" in names)
                require("conflicts" in names)
                require("inventory_counts" in names)
            }
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        Room.databaseBuilder(ctx, IpvDatabase::class.java, dbName)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
            .close()
    }
}
