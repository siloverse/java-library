package io.github.siloverse.pagination.spring.keyset

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import javax.sql.DataSource

/**
 * The proving ground, step 2: the same questions asked through Spring Data JPA.
 * Which SQL does Hibernate write for keyset scrolling, and does Postgres seek on it?
 * What happens to a page limit when the photos are fetched in the same query?
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeysetJpaProvingGroundTest {

    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var repository: ListingRepository
    private lateinit var jdbc: JdbcTemplate

    @BeforeAll
    fun start() {
        context = ListingJpaApp.start(ListingFixture.start())
        repository = context.getBean(ListingRepository::class.java)
        jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
    }

    @AfterAll
    fun stop() = context.close()

    @BeforeEach
    fun resetRecorder() = RecordingStatementInspector.reset()

    @Test
    fun `spring data keyset scrolling visits every row exactly once, in order`() {
        val visited = mutableListOf<UUID>()
        var window = repository.findFirst25ByOrderByCreatedAtAscIdAsc(ScrollPosition.keyset())
        while (true) {
            visited += window.content.map { it.id }
            if (!window.hasNext()) break
            window = repository.findFirst25ByOrderByCreatedAtAscIdAsc(window.positionAt(window.size() - 1))
        }

        assertThat(visited).hasSize(5000)
        assertThat(visited).doesNotHaveDuplicates()
        assertThat(visited).isEqualTo(ListingFixture.totalOrder(jdbc))
    }

    @Test
    fun `spring data writes the OR chain for keyset, so Postgres walks the index and filters`() {
        val first = repository.findFirst25ByOrderByCreatedAtAscIdAsc(ScrollPosition.keyset())
        RecordingStatementInspector.reset()
        repository.findFirst25ByOrderByCreatedAtAscIdAsc(first.positionAt(first.size() - 1))

        val sql = RecordingStatementInspector.selectsOn("listing").single()
        val where = sql.substringAfter(" where ").substringBefore(" order by ")
        // Hibernate 7.4: created_at>? or created_at=? and id>?   — not (created_at, id) > (?, ?)
        assertThat(where).contains(" or ")
        assertThat(where).doesNotContain("(l1_0.created_at,l1_0.id)")

        // Bind the boundary values in the order the placeholders appear, then ask Postgres for the plan.
        val boundary = first.content.last()
        val args: List<Any> = Regex("""(created_at|id)[=><]+\?""").findAll(where).map {
            if (it.groupValues[1] == "created_at") boundary.createdAt else boundary.id
        }.toList()
        val plan = ListingFixture.explain(jdbc, sql.replace("fetch first ? rows only", "fetch first 26 rows only"), *args.toTypedArray())

        // Index Scan, not Index Only: Hibernate also selects title, which is not in the index.
        // Filter, not Index Cond: Postgres walks the index from the start and discards. Same as step 1.
        assertThat(plan).contains("Index Scan using listing_created_at_id", "Filter:")
        assertThat(plan).doesNotContain("Index Cond")
    }

    @Test
    fun `hibernate 7 pages the parent in a subquery before joining the collection, no in-memory limit`() {
        val page = repository.findAllWithPhotosBy(PageRequest.of(0, 10, Sort.by("createdAt", "id")))

        // two selects on listing: the page and the count a Page needs; the page is the one with the join
        val sql = RecordingStatementInspector.selectsOn("listing").single { it.contains("left join") }
        // from (select ... from listing ... order by ... offset ? rows fetch first ? rows only) l1_0 left join listing_photo ...
        assertThat(sql).matches("""select .* from \(select .* from listing l1_0 order by .* offset \? rows fetch first \? rows only\) l1_0\(.*\) left join listing_photo .*""")
        assertThat(page.content).hasSize(10)
        assertThat(page.content.map { it.id }).isEqualTo(ListingFixture.totalOrder(jdbc).take(10))
        // the photos arrived in the same statement: no second select on listing_photo
        assertThat(RecordingStatementInspector.statements.filter { it.contains("from listing_photo") }).isEmpty()
        assertThat(page.content.sumOf { it.photos.size }).isGreaterThan(0)
    }
}
