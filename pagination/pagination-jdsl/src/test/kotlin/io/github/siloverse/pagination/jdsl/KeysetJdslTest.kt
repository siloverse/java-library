package io.github.siloverse.pagination.jdsl

import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import io.github.siloverse.pagination.core.keyset.KeysetKey
import io.github.siloverse.pagination.core.keyset.KeysetKeys
import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.policy.PaginationPolicy
import io.github.siloverse.pagination.core.sort.SortRequest
import io.github.siloverse.pagination.core.sort.SortableFields
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
 * Keyset paging through JDSL on a real database, with the client choosing the sort.
 * Every expected order is computed with plain SQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeysetJdslTest {

    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var repository: ListingRepository
    private lateinit var jdbc: JdbcTemplate

    /** The order contract of the list, written once. */
    private val createdAt = KeysetKey.offsetDateTime("createdAt")
    private val price = KeysetKey.long("price")
    private val id = KeysetKey.uuid("id")
    private val sortable = SortableFields(createdAt, price, id, tieBreaker = "id")

    private val keyset = JdslKeyset<Listing>(sortable) {
        bind(createdAt, Listing::createdAt)
        bind(price, Listing::price)
        bind(id, Listing::id)
    }

    private val policy = PaginationPolicy(defaultSize = 25, maxSize = 100)

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

    /** What a service method looks like: sort and page from the client, filter from the selector. */
    private fun page(sort: List<String>?, after: String?, filter: (com.linecorp.kotlinjdsl.dsl.jpql.Jpql.() -> Predicatable?)? = null): KeysetPage<Listing> {
        val keys = sortable.keysFor(SortRequest.parse(sortable, sort))
        val request = policy.keyset(after = after, size = null)
        return repository.findAll(limit = request.size + 1) {
            select(entity(Listing::class))
                .from(entity(Listing::class))
                .where(and(filter?.invoke(this), keysetAfter(request, keys, keyset)))
                .orderBy(*keysetOrder(keys, keyset).toTypedArray())
        }.toKeysetPage(request, keys, keyset)
    }

    private fun walk(sort: List<String>?, filter: (com.linecorp.kotlinjdsl.dsl.jpql.Jpql.() -> Predicatable?)? = null): List<UUID> {
        val visited = mutableListOf<UUID>()
        var page = page(sort, after = null, filter = filter)
        while (true) {
            visited += page.items.map { it.id }
            page = page(sort, after = (page.nextCursor ?: break).value, filter = filter)
        }
        return visited
    }

    private fun sqlOrder(orderBy: String, where: String = "true"): List<UUID> =
        jdbc.queryForList("SELECT id FROM listing WHERE $where ORDER BY $orderBy", UUID::class.java).requireNoNulls()

    @Test
    fun `default sort walks every row once, in order, with one row-value seek per page`() {
        val visited = walk(sort = null)

        assertThat(visited).hasSize(1000).doesNotHaveDuplicates().isEqualTo(sqlOrder("id"))
    }

    @Test
    fun `same-direction keys become one row-value comparison, and Postgres seeks on it`() {
        val first = page(sort = listOf("createdAt"), after = null)
        RecordingStatementInspector.reset()
        page(sort = listOf("createdAt"), after = first.nextCursor!!.value)

        val sql = RecordingStatementInspector.lastSelect()
        assertThat(sql).contains("(l1_0.created_at,l1_0.id)>(?,?)", "order by l1_0.created_at,l1_0.id")

        val last = first.items.last()
        val plan = jdbc.queryForList(
            "EXPLAIN " + sql.replace("fetch first ? rows only", "fetch first 26 rows only"),
            String::class.java, last.createdAt, last.id,
        ).joinToString("\n")
        assertThat(plan).contains("Index Cond: (ROW(created_at, id) >")
        assertThat(plan).doesNotContain("Filter")
    }

    @Test
    fun `all-descending keys compare with less-than`() {
        val visited = walk(sort = listOf("createdAt,desc", "id,desc"))

        assertThat(visited).hasSize(1000).isEqualTo(sqlOrder("created_at DESC, id DESC"))
        assertThat(RecordingStatementInspector.lastSelect()).contains("(l1_0.created_at,l1_0.id)<(?,?)")
    }

    @Test
    fun `mixed directions fall back to the OR chain, still visiting every row once`() {
        val visited = walk(sort = listOf("price,desc"))

        assertThat(visited).hasSize(1000).doesNotHaveDuplicates().isEqualTo(sqlOrder("price DESC, id ASC"))
        assertThat(RecordingStatementInspector.lastSelect()).contains("l1_0.price<?", " or ", "l1_0.price=?", "l1_0.id>?")
    }

    @Test
    fun `a filter and the cursor compose into one query`() {
        val visited = walk(sort = listOf("createdAt")) { path(Listing::status).equal("open") }

        assertThat(visited).isEqualTo(sqlOrder("created_at, id", where = "status = 'open'")).isNotEmpty()
        assertThat(visited.size).isLessThan(1000)
    }

    @Test
    fun `the cursor holds the last row's key values, and the last page has none`() {
        val first = page(sort = listOf("price,desc"), after = null)
        val last = first.items.last()

        assertThat(first.nextCursor!!.parts()).containsExactly(last.price.toString(), last.id.toString())

        val order = sqlOrder("price DESC, id ASC")
        val boundary = repository.findById(order[994]).get()
        val keys = sortable.keysFor(SortRequest.parse(sortable, listOf("price,desc")))
        val lastPage = page(sort = listOf("price,desc"), after = keys.cursorOf(listOf(boundary.price, boundary.id)).value)
        assertThat(lastPage.items.map { it.id }).isEqualTo(order.takeLast(5))
        assertThat(lastPage.nextCursor).isNull()
    }

    @Test
    fun `a cursor from another sort is a client error`() {
        val fromPrice = page(sort = listOf("price,desc"), after = null).nextCursor!!

        assertThatThrownBy { page(sort = listOf("createdAt"), after = fromPrice.value) }
            .hasMessageContaining("after: is not a valid cursor")
    }

    @Test
    fun `a sortable field left unbound is refused when the list is written`() {
        assertThatThrownBy { JdslKeyset<Listing>(sortable) { bind(id, Listing::id) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("sortable fields with no binding: [createdAt, price]")
    }

    @Test
    fun `an unknown sort field is a 400 before any query runs`() {
        assertThatThrownBy { page(sort = listOf("status"), after = null) }
            .hasMessageContaining("sort: 'status' is not sortable")
    }
}
