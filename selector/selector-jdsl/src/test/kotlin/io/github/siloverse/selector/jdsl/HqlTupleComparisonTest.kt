package io.github.siloverse.selector.jdsl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import javax.sql.DataSource

/**
 * The experiment: HQL 7 documents tuple comparison, `(a, b) > (x, y)`. Kotlin JDSL has no
 * builder for it but has `customPredicate` with raw JPQL. If Hibernate turns the tuple into a
 * SQL row value, keyset paging through JDSL gets the index seek that Spring Data scrolling
 * cannot. If Hibernate expands it to the OR chain, nothing is gained. The SQL decides.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HqlTupleComparisonTest {

    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var repository: ListingRepository
    private lateinit var jdbc: JdbcTemplate

    @BeforeAll
    fun start() {
        context = ListingJdslApp.start()
        repository = context.getBean(ListingRepository::class.java)
        jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
    }

    @AfterAll
    fun stop() = context.close()

    @BeforeEach
    fun resetRecorder() = RecordingStatementInspector.reset()

    @Test
    fun `a tuple comparison through customPredicate becomes a SQL row value, and Postgres seeks on it`() {
        val order = jdbc.queryForList("SELECT id FROM listing ORDER BY created_at, id", UUID::class.java).requireNoNulls()
        val boundary = repository.findById(order[499]).get()

        val rows = repository.findAll(limit = 26) {
            select(entity(Listing::class))
                .from(entity(Listing::class))
                .where(
                    customPredicate(
                        "({0}, {1}) > ({2}, {3})",
                        path(Listing::createdAt), path(Listing::id), value(boundary.createdAt), value(boundary.id),
                    ),
                )
                .orderBy(path(Listing::createdAt).asc(), path(Listing::id).asc())
        }.map { it!!.id }

        assertThat(rows).isEqualTo(order.subList(500, 526))

        // Hibernate 7.4 keeps the tuple as a row value; it does not expand it to the OR chain.
        val sql = RecordingStatementInspector.lastSelect()
        val where = sql.substringAfter(" where ").substringBefore(" order by ")
        assertThat(where).isEqualTo("(l1_0.created_at,l1_0.id)>(?,?)")

        // And Postgres seeks: Index Cond on the row value, no Filter. The form Spring Data scrolling cannot produce.
        val plan = jdbc.queryForList(
            "EXPLAIN " + sql.replace("fetch first ? rows only", "fetch first 26 rows only"),
            String::class.java,
            *bindArgs(where, boundary),
        ).joinToString("\n")
        assertThat(plan).contains("Index Scan using listing_created_at_id", "Index Cond: (ROW(created_at, id) >")
        assertThat(plan).doesNotContain("Filter", "Seq Scan", "Sort")
    }

    /** Binds the boundary values in placeholder order: the columns appear in the SQL in the same order as the `?`s. */
    private fun bindArgs(where: String, boundary: Listing): Array<Any> {
        val names = Regex("""\b(created_at|id)\b""").findAll(where).map { it.groupValues[1] }.toList()
        check(names.size == where.count { it == '?' }) { "cannot bind: $where" }
        return names.map { if (it == "created_at") boundary.createdAt else boundary.id }.toTypedArray()
    }
}
