package cu.ipvgc.server.db

import cu.ipvgc.seed.SyntheticSeed
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

class MigrationReproducibilityIT {
    @Test
    fun `migrations from scratch produce the same schema twice`() {
        schemaFingerprint().let { first ->
            schemaFingerprint() shouldBe first
        }
    }

    @Test
    fun `second migrate on the same database is a no-op`() {
        PostgreSQLContainer("postgres:17-alpine").use { pg ->
            pg.start()
            repeat(2) {
                SyntheticSeed.migrate(pg.jdbcUrl, pg.username, pg.password)
            }
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { connection ->
                connection.createStatement().use { st ->
                    st.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success").use { rs ->
                        rs.next()
                        rs.getInt(1) shouldBe 12
                    }
                }
            }
        }
    }

    private fun schemaFingerprint(): String {
        PostgreSQLContainer("postgres:17-alpine").use { pg ->
            pg.start()
            SyntheticSeed.migrate(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { connection ->
                val sql = """
                    SELECT table_name || '.' || column_name || ':' || data_type || ':' || is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                    ORDER BY 1
                """.trimIndent()
                val columns = connection.createStatement().use { st ->
                    st.executeQuery(sql).use { rs ->
                        buildString {
                            while (rs.next()) appendLine(rs.getString(1))
                        }
                    }
                }
                val constraints = connection.createStatement().use { st ->
                    st.executeQuery(
                        """
                        SELECT conrelid::regclass::text || ':' || contype || ':' || pg_get_constraintdef(oid)
                        FROM pg_constraint
                        WHERE connamespace = 'public'::regnamespace
                        ORDER BY 1
                        """.trimIndent(),
                    ).use { rs ->
                        buildString {
                            while (rs.next()) appendLine(rs.getString(1))
                        }
                    }
                }
                return columns + constraints
            }
        }
    }
}


