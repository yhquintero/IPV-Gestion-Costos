package cu.ipvgc.seed

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.flywaydb.core.Flyway
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

data class TestRatesFile(
    val note: String? = null,
    val as_of_local: String? = null,
    val instruments: Map<String, String>,
)

object SeedIds {
    val ORG_ALPHA: UUID = UUID.fromString("11111111-1111-7000-8000-000000000001")
    val ORG_BETA: UUID = UUID.fromString("22222222-2222-7000-8000-000000000001")
    val SNAPSHOT: UUID = UUID.fromString("33333333-3333-7000-8000-000000000001")
    val COMPANY_ALPHA: UUID = UUID.fromString("11111111-1111-7000-8000-000000000010")
}

object SyntheticSeed {
    const val SNAPSHOT_HASH_HEX = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

    fun migrate(jdbcUrl: String, user: String, password: String) {
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("filesystem:${migrationsDir()}")
            .load()
            .migrate()
    }

    fun apply(connection: Connection) {
        insertTestRates(connection)
        connection.createStatement().use { st ->
            st.execute(loadResource("/seed/synthetic.sql"))
        }
    }

    fun loadTestRates(): TestRatesFile {
        val mapper = jacksonObjectMapper()
        return mapper.readValue(loadResource("/seed/exchange-rates-test.json"))
    }

    fun insertTestRates(connection: Connection) {
        val file = loadTestRates()
        val insertSample = connection.prepareStatement(
            """
            INSERT INTO exchange_rate_samples
                (id, instrument_code, value, source, source_ts_raw, is_test)
            VALUES (?, ?, ?, 'SEED_TEST', ?::jsonb, true)
            """.trimIndent(),
        )
        val insertCurrent = connection.prepareStatement(
            """
            INSERT INTO exchange_rate_current (instrument_code, sample_id, status)
            VALUES (?, ?, 'TEST')
            ON CONFLICT (instrument_code) DO UPDATE
                SET sample_id = EXCLUDED.sample_id, status = 'TEST', updated_at = clock_timestamp()
            """.trimIndent(),
        )
        val insertItem = connection.prepareStatement(
            """
            INSERT INTO rate_snapshot_items (snapshot_id, instrument_code, sample_id, value)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        )

        connection.prepareStatement(
            """
            INSERT INTO rate_snapshots (id, content_hash, status_at_capture, is_test)
            VALUES (?, decode(?, 'hex'), 'TEST', true)
            """.trimIndent(),
        ).use { ps ->
            ps.setObject(1, SeedIds.SNAPSHOT)
            ps.setString(2, SNAPSHOT_HASH_HEX)
            ps.executeUpdate()
        }

        file.instruments.entries.forEachIndexed { index, (code, value) ->
            val sampleId = UUID.fromString("33333333-3333-7000-8000-%012d".format(index + 1))
            insertSample.setObject(1, sampleId)
            insertSample.setString(2, code)
            insertSample.setBigDecimal(3, BigDecimal(value))
            insertSample.setString(4, """{"as_of_local":"${file.as_of_local}"}""")
            insertSample.executeUpdate()

            insertCurrent.setString(1, code)
            insertCurrent.setObject(2, sampleId)
            insertCurrent.executeUpdate()

            insertItem.setObject(1, SeedIds.SNAPSHOT)
            insertItem.setString(2, code)
            insertItem.setObject(3, sampleId)
            insertItem.setBigDecimal(4, BigDecimal(value))
            insertItem.executeUpdate()
        }
        insertSample.close()
        insertCurrent.close()
        insertItem.close()
    }

    fun open(jdbcUrl: String, user: String, password: String): Connection =
        DriverManager.getConnection(jdbcUrl, user, password)

    private fun loadResource(path: String): String {
        val stream = checkNotNull(SyntheticSeed::class.java.getResourceAsStream(path)) {
            "missing classpath resource $path"
        }
        return stream.bufferedReader().use { it.readText() }
    }

    internal fun migrationsDir(): String {
        val fromProp = System.getProperty("ipvgc.migrationsDir")
        if (!fromProp.isNullOrBlank()) return fromProp
        val candidates = listOf(
            "server/db/migration",
            "../server/db/migration",
            "../../server/db/migration",
            "../db/migration",
            "db/migration",
        )
        val found = candidates.firstOrNull { java.io.File(it).isDirectory }
        return checkNotNull(found) { "cannot locate server/db/migration (cwd=${java.io.File(".").absolutePath})" }
    }
}

fun main(args: Array<String>) {
    val url = args.getOrNull(0) ?: System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/ipvgc"
    val user = args.getOrNull(1) ?: System.getenv("DB_MIGRATOR_USER") ?: "ipvgc"
    val password = args.getOrNull(2) ?: System.getenv("DB_MIGRATOR_PASSWORD") ?: "ipvgc"
    SyntheticSeed.migrate(url, user, password)
    SyntheticSeed.open(url, user, password).use { connection ->
        connection.autoCommit = false
        SyntheticSeed.apply(connection)
        connection.commit()
    }
    println("Synthetic seed applied.")
}
