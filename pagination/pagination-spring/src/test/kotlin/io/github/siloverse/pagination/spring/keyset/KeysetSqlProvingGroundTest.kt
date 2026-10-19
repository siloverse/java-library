package io.github.siloverse.pagination.spring.keyset

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The proving ground: a real Postgres, hand-written SQL, no JPA. Each test shows one thing
 * that an in-memory list or H2 would hide. The adapter in step 3 is built on what these prove.
 *
 * Data: 5000 listings, ten per second, so every `created_at` value is shared by ten rows.
 * Each listing has 0 to 3 photos. Index on `(created_at, id)`, the same columns as the sort.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeysetSqlProvingGroundTest {

    private val postgres = PostgreSQLContainer("postgres:17-alpine")
    private lateinit var jdbc: JdbcTemplate

    data class Row(val id: UUID, val createdAt: OffsetDateTime)

    private val rowMapper = { rs: java.sql.ResultSet, _: Int ->
        Row(rs.getObject("id", UUID::class.java), rs.getObject("created_at", OffsetDateTime::class.java))
    }

    @BeforeAll
    fun startDatabaseAndSeed() {
        postgres.start()
        jdbc = JdbcTemplate(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password))
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

    private val totalOrder: List<UUID>
        get() = jdbc.query("SELECT id, created_at FROM listing ORDER BY created_at, id", rowMapper).map { it.id }

    /** WHERE (created_at, id) > (?, ?) — the row-value comparison, with the tie breaker. */
    private fun pageWithTieBreaker(after: Row?, size: Int): List<Row> =
        if (after == null) {
            jdbc.query("SELECT id, created_at FROM listing ORDER BY created_at, id LIMIT ?", rowMapper, size)
        } else {
            jdbc.query(
                "SELECT id, created_at FROM listing WHERE (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT ?",
                rowMapper, after.createdAt, after.id, size,
            )
        }

    /** WHERE created_at > ? — the naive version, no tie breaker. */
    private fun pageWithoutTieBreaker(after: Row?, size: Int): List<Row> =
        if (after == null) {
            jdbc.query("SELECT id, created_at FROM listing ORDER BY created_at LIMIT ?", rowMapper, size)
        } else {
            jdbc.query(
                "SELECT id, created_at FROM listing WHERE created_at > ? ORDER BY created_at LIMIT ?",
                rowMapper, after.createdAt, size,
            )
        }

    private fun walk(size: Int, page: (Row?, Int) -> List<Row>): List<UUID> {
        val visited = mutableListOf<UUID>()
        var last: Row? = null
        while (true) {
            val rows = page(last, size)
            if (rows.isEmpty()) return visited
            visited += rows.map { it.id }
            last = rows.last()
        }
    }

    @Test
    fun `with the tie breaker, walking the cursor visits every row exactly once, in order`() {
        val visited = walk(size = 25, page = ::pageWithTieBreaker)

        assertThat(visited).hasSize(5000)
        assertThat(visited).doesNotHaveDuplicates()
        assertThat(visited).isEqualTo(totalOrder)
    }

    @Test
    fun `without the tie breaker, rows that share a timestamp with the page boundary are skipped`() {
        // page size 25 ends in the middle of a ten-row timestamp group; "created_at > ?" jumps past the rest
        val visited = walk(size = 25, page = ::pageWithoutTieBreaker)

        assertThat(visited).hasSizeLessThan(5000)
        val skipped = 5000 - visited.size
        assertThat(skipped).isGreaterThan(0)
    }

    @Test
    fun `postgres rounds to microseconds, so the cursor must carry what the database returned`() {
        // Java Instant has nanoseconds; Postgres timestamptz has microseconds and ROUNDS, it does not truncate.
        // A cursor built from the value the application created would point at a row that does not exist.
        val id = UUID.randomUUID()
        val appValue = Instant.parse("2026-06-01T00:00:00.123456789Z").atOffset(ZoneOffset.UTC)
        jdbc.update("INSERT INTO listing (id, title, created_at) VALUES (?, ?, ?)", id, "precision", appValue)

        val stored = jdbc.queryForObject("SELECT created_at FROM listing WHERE id = ?", OffsetDateTime::class.java, id)!!

        assertThat(stored).isNotEqualTo(appValue)
        assertThat(stored.toInstant()).isEqualTo(Instant.parse("2026-06-01T00:00:00.123457Z"))
        assertThat(stored.toInstant()).isNotEqualTo(appValue.toInstant().truncatedTo(ChronoUnit.MICROS))
        jdbc.update("DELETE FROM listing WHERE id = ?", id)
    }

    @Test
    fun `the row-value comparison is served by the composite index`() {
        val boundary = pageWithTieBreaker(null, 2500).last()
        val plan = explain(
            "SELECT id, created_at FROM listing WHERE (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT 25",
            boundary.createdAt, boundary.id,
        )

        // Index Only Scan: the index holds both columns, so the table is not even touched.
        // Index Cond on ROW(...): the database seeks straight to the boundary. Same cost at any depth.
        assertThat(plan).contains("Index Only Scan using listing_created_at_id", "Index Cond: (ROW(created_at, id) >")
        assertThat(plan).doesNotContain("Seq Scan", "Sort", "Filter")
    }

    @Test
    fun `the OR chain form, which ORMs write, walks the index from the start and filters`() {
        // Same index, same order, but no seek: Postgres reads index entries from the beginning and
        // throws them away until 25 pass the Filter. The deeper the page, the more it reads.
        // That is the offset cost coming back through a side door.
        val boundary = pageWithTieBreaker(null, 2500).last()
        val plan = explain(
            "SELECT id, created_at FROM listing WHERE created_at > ? OR (created_at = ? AND id > ?) ORDER BY created_at, id LIMIT 25",
            boundary.createdAt, boundary.createdAt, boundary.id,
        )

        assertThat(plan).contains("Index Only Scan using listing_created_at_id", "Filter:")
        assertThat(plan).doesNotContain("Index Cond")
    }

    @Test
    fun `a collection join plus LIMIT pages rows, not listings`() {
        val joined = jdbc.queryForList(
            """
            SELECT l.id FROM listing l
            LEFT JOIN listing_photo p ON p.listing_id = l.id
            ORDER BY l.created_at, l.id LIMIT 10
            """,
            UUID::class.java,
        )

        assertThat(joined).hasSize(10)
        assertThat(joined.distinct()).hasSizeLessThan(10)
    }

    @Test
    fun `the fix is to page the parent first, then join the children`() {
        val joined = jdbc.queryForList(
            """
            SELECT l.id FROM (
                SELECT id, created_at FROM listing ORDER BY created_at, id LIMIT 10
            ) l
            LEFT JOIN listing_photo p ON p.listing_id = l.id
            ORDER BY l.created_at, l.id
            """,
            UUID::class.java,
        )

        assertThat(joined.distinct()).hasSize(10)
        assertThat(joined.distinct()).isEqualTo(totalOrder.take(10))
    }

    private fun explain(sql: String, vararg args: Any): String =
        jdbc.queryForList("EXPLAIN $sql", String::class.java, *args).joinToString("\n")
}
