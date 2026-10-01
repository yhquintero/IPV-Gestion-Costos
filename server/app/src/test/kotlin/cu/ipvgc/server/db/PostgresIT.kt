package cu.ipvgc.server.db

import cu.ipvgc.seed.SeedIds
import cu.ipvgc.seed.SyntheticSeed
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

object SharedPostgres {
    val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("ipvgc")
        .withUsername("postgres")
        .withPassword("postgres")

    val ready: Boolean by lazy {
        container.start()
        SyntheticSeed.migrate(container.jdbcUrl, container.username, container.password)
        SyntheticSeed.open(container.jdbcUrl, container.username, container.password).use { connection ->
            connection.autoCommit = false
            SyntheticSeed.apply(connection)
            connection.commit()
        }
        true
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PostgresIT {
    @BeforeAll
    fun migrateAndSeed() {
        check(SharedPostgres.ready)
    }

    protected val postgres: PostgreSQLContainer<*> get() = SharedPostgres.container

    protected fun admin(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    protected fun asApp(org: UUID): Connection {
        val connection = admin()
        connection.autoCommit = true
        connection.createStatement().use { it.execute("SET ROLE app_rw") }
        connection.prepareStatement("SELECT set_config('app.organization_id', ?, false)").use { ps ->
            ps.setString(1, org.toString())
            ps.execute()
        }
        return connection
    }

    protected fun Connection.scalarLong(sql: String): Long =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                check(rs.next())
                rs.getLong(1)
            }
        }

    protected fun Connection.scalarString(sql: String): String? =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        }

    protected fun expectFailure(sql: String, connection: Connection, vararg needles: String) {
        try {
            connection.createStatement().use { it.execute(sql) }
            throw AssertionError("expected SQL to fail ($sql)")
        } catch (ex: SQLException) {
            val text = buildString {
                var cur: SQLException? = ex
                while (cur != null) {
                    append(cur.message).append(' ')
                    cur = cur.nextException
                }
            }.lowercase()
            if (needles.isNotEmpty() && needles.none { text.contains(it.lowercase()) }) {
                throw AssertionError("failure did not mention ${needles.toList()}: $text", ex)
            }
        }
    }

    protected val alpha: UUID = SeedIds.ORG_ALPHA
    protected val beta: UUID = SeedIds.ORG_BETA
}
