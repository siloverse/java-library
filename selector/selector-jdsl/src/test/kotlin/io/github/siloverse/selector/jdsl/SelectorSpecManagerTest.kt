package io.github.siloverse.selector.jdsl

import io.github.siloverse.selector.core.error.SelectorException
import io.github.siloverse.selector.core.selector.Selectors
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
 * The manager on a real database: query params in, rows out, and the SQL Hibernate ran in
 * between. Every expected set is computed with plain SQL so the test does not trust the code
 * it tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SelectorSpecManagerTest {

    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var repository: ListingRepository
    private lateinit var jdbc: JdbcTemplate

    /** What Spring's scanning gives the manager: every spec bean. */
    private val manager = SelectorSpecManager(
        listOf(
            ListingIdSpec(), ListingStatusSpec(), ListingStatusNotSpec(), ListingStatusInSpec(),
            ListingPriceLtSpec(), ListingPriceGteSpec(), ListingActiveSpec(), ListingSearchSpec(), ListingNotCheapestSpec(),
        ),
    )

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

    /** What a service method looks like: the resolver's selectors in, one query with the manager's WHERE. */
    private fun find(vararg params: Pair<String, String>): List<UUID> {
        val selectors = Selectors.parse(Listing::class, params.groupBy({ it.first }, { it.second }))
        return repository.findAll {
            select(entity(Listing::class))
                .from(entity(Listing::class))
                .where(manager.apply(selectors))
                .orderBy(path(Listing::price).asc())
        }.map { it!!.id }
    }

    private fun sql(where: String): List<UUID> =
        jdbc.queryForList("SELECT id FROM listing WHERE $where ORDER BY price", UUID::class.java).requireNoNulls()

    @Test
    fun `no filter returns everything`() {
        assertThat(find()).hasSize(1000).isEqualTo(sql("true"))
    }

    @Test
    fun `a single-value spec`() {
        assertThat(find("filter.listing.status" to "open")).isEqualTo(sql("status = 'open'")).isNotEmpty()
    }

    @Test
    fun `selectors are ANDed, each typed by its spec`() {
        assertThat(find("filter.listing.price.lt" to "10", "filter.listing.active" to "true")).isEqualTo(sql("price < 10 AND active"))
        assertThat(find("filter.listing.price.gte" to "990", "filter.listing.status.not" to "draft")).isEqualTo(sql("price >= 990 AND status <> 'draft'"))
    }

    @Test
    fun `a multi-value spec takes the repeated parameter`() {
        val ids = sql("price IN (1, 2, 3)")

        assertThat(find(*ids.map { "filter.listing.id" to it.toString() }.toTypedArray())).isEqualTo(ids)
        assertThat(find("filter.listing.status.in" to "open", "filter.listing.status.in" to "closed")).isEqualTo(sql("status IN ('open', 'closed')"))
    }

    @Test
    fun `a spec that is not a column`() {
        assertThat(find("filter.listing.search" to "ing 42")).isEqualTo(sql("title LIKE '%ing 42%'")).isNotEmpty()
    }

    @Test
    fun `a spec with a subquery`() {
        assertThat(find("filter.listing.not-cheapest" to "true"))
            .isEqualTo(sql("price > (SELECT min(o.price) FROM listing o WHERE o.status = listing.status)")).isNotEmpty()
        assertThat(find("filter.listing.not-cheapest" to "false")).hasSize(3)
    }

    @Test
    fun `values are bound as parameters, never written into the query`() {
        find("filter.listing.status" to "open", "filter.listing.price.lt" to "10")

        val sql = RecordingStatementInspector.lastSelect()
        assertThat(sql).contains("where (l1_0.status=?) and (l1_0.price<?)")
        assertThat(sql).doesNotContain("open", "10")
    }

    @Test
    fun `an unknown filter is a client error that lists the entity's filters`() {
        assertThatThrownBy { find("filter.listing.prize" to "1") }
            .isInstanceOf(SelectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "filter.listing.prize")
            .hasMessageContaining("unknown filter; filters for Listing: [filter.listing.active, filter.listing.id")
    }

    @Test
    fun `a wrong value count and an unreadable value are client errors naming the key`() {
        assertThatThrownBy { find("filter.listing.status" to "open", "filter.listing.status" to "closed") }
            .hasMessage("filter.listing.status: takes exactly one value, got 2")
        assertThatThrownBy { find("filter.listing.price.lt" to "cheap") }
            .hasMessage("filter.listing.price.lt: 'cheap' is not a valid long")
    }

    @Test
    fun `two specs for one key is a startup error`() {
        assertThatThrownBy { SelectorSpecManager(listOf(ListingStatusSpec(), ListingStatusSpec())) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("2 specs for Listing filter.listing.status")
    }
}
