package io.github.siloverse.pagination.spring.keyset

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * The proving-ground data, shared by the SQL and the JPA tests.
 *
 * 5000 listings, ten per second, so every `created_at` value is shared by ten rows.
 * Each listing has 0 to 3 photos. Index on `(created_at, id)`, the same columns as the sort.
 */
object ListingFixture {

    data class Row(val id: UUID, val createdAt: OffsetDateTime)

    val rowMapper = { rs: java.sql.ResultSet, _: Int ->
        Row(rs.getObject("id", UUID::class.java), rs.getObject("created_at", OffsetDateTime::class.java))
    }

    fun start(): PostgreSQLContainer {
        val postgres = PostgreSQLContainer("postgres:17-alpine")
        postgres.start()
        seed(JdbcTemplate(dataSource(postgres)))
        return postgres
    }

    fun dataSource(postgres: PostgreSQLContainer): DataSource =
        DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun seed(jdbc: JdbcTemplate) {
        jdbc.execute("CREATE TABLE listing (id uuid PRIMARY KEY, title text NOT NULL, created_at timestamptz NOT NULL)")
        jdbc.execute("CREATE TABLE listing_photo (id bigserial PRIMARY KEY, listing_id uuid NOT NULL REFERENCES listing (id), url text NOT NULL)")
        jdbc.execute("CREATE INDEX listing_created_at_id ON listing (created_at, id)")
        jdbc.execute(
            """
            INSERT INTO listing (id, title, created_at)
            SELECT gen_random_uuid(), 'listing ' || g, timestamptz '2026-01-01 00:00:00+00' + (g / 10) * interval '1 second'
            FROM generate_series(1, 5000) g
            """,
        )
        jdbc.execute(
            """
            INSERT INTO listing_photo (listing_id, url)
            SELECT l.id, 'photo-' || p
            FROM listing l, generate_series(1, 3) p
            WHERE p <= abs(hashtext(l.id::text)) % 4
            """,
        )
        jdbc.execute("ANALYZE listing")
        jdbc.execute("ANALYZE listing_photo")
    }

    /** The truth every walk is compared against: all ids in index order. */
    fun totalOrder(jdbc: JdbcTemplate): List<UUID> =
        jdbc.query("SELECT id, created_at FROM listing ORDER BY created_at, id", rowMapper).map { it.id }

    fun explain(jdbc: JdbcTemplate, sql: String, vararg args: Any): String =
        jdbc.queryForList("EXPLAIN $sql", String::class.java, *args).joinToString("\n")
}
