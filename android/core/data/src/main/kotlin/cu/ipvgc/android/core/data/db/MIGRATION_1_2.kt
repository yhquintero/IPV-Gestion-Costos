package cu.ipvgc.android.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS outbox (
                    mutationId TEXT NOT NULL PRIMARY KEY,
                    seqNo INTEGER NOT NULL,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    op TEXT NOT NULL,
                    baseVersion INTEGER,
                    payloadJson TEXT NOT NULL,
                    state TEXT NOT NULL,
                    attempts INTEGER NOT NULL,
                    lastError TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conflicts (
                    mutationId TEXT NOT NULL PRIMARY KEY,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    serverJson TEXT,
                    clientJson TEXT,
                    resultCode TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS inventory_counts (
                    id TEXT NOT NULL PRIMARY KEY,
                    businessDate TEXT NOT NULL,
                    observedQty TEXT NOT NULL,
                    itemCode TEXT NOT NULL,
                    syncState TEXT NOT NULL,
                    lastSyncedAt INTEGER
                )
                """.trimIndent(),
            )
        }
    }
