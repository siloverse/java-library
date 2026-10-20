package io.github.siloverse.pagination.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import io.github.siloverse.pagination.core.keyset.KeysetKey
import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.policy.PaginationPolicy
import io.github.siloverse.pagination.core.sort.Direction
import io.github.siloverse.pagination.core.sort.SortOrder
import io.github.siloverse.pagination.core.sort.SortRegistry
import io.github.siloverse.pagination.core.sort.SortRequest
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

    /** The order contract of the list, written once. `createdAt` uses the path form to prove that branch. */
    class ListingSort : SortSpec<Listing>(Listing::class, tieBreaker = "id", defaultSort = listOf(SortOrder("createdAt"))) {
        val createdAt = sortable(KeysetKey.offsetDateTime("createdAt"), path = { path(Listing::createdAt) }, value = { it.createdAt })
        val price = sortable(KeysetKey.long("price"), Listing::price)
        val id = sortable(KeysetKey.uuid("id"), Listing::id)
    }

    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var repository: ListingRepository
    private lateinit var jdbc: JdbcTemplate

    private val spec = ListingSort()
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

    /** What the resolver does: parse the sort against the contract, apply the policy. */
    private fun request(sort: List<String>?, after: String?): KeysetRequest =
        policy.keyset(after = after, size = null, sort = SortRequest.parse(spec.sortable, sort))

    /** What a service method looks like: filter from the selector, sort and cursor from the request. */
    private fun page(request: KeysetRequest, filter: (Jpql.() -> Predicatable?)? = null): KeysetPage<Listing> =
        repository.findAll(limit = request.size + 1) {
            select(entity(Listing::class))
                .from(entity(Listing::class))
                .where(and(filter?.invoke(this), keysetAfter(request, spec)))
                .orderBy(*keysetOrder(request, spec).toTypedArray())
        }.toKeysetPage(request, spec)

    private fun walk(sort: List<String>?, filter: (Jpql.() -> Predicatable?)? = null): List<UUID> {
        val visited = mutableListOf<UUID>()
        var page = page(request(sort, after = null), filter)
        while (true) {
            visited += page.items.map { it.id }
            page = page(request(sort, after = (page.nextCursor ?: break).value), filter)
        }
        return visited
    }

    private fun sqlOrder(orderBy: String, where: String = "true"): List<UUID> =
        jdbc.queryForList("SELECT id FROM listing WHERE $where ORDER BY $orderBy", UUID::class.java).requireNoNulls()

    @Test
    fun `no sort means the contract's default, and the walk visits every row once, in order`() {
        assertThat(walk(sort = null)).hasSize(1000).doesNotHaveDuplicates().isEqualTo(sqlOrder("created_at, id"))
    }

    @Test
    fun `same-direction keys become one row-value comparison, and Postgres seeks on it`() {
        val first = page(request(listOf("createdAt"), after = null))
        RecordingStatementInspector.reset()
        page(request(listOf("createdAt"), after = first.nextCursor!!.value))

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
        assertThat(walk(sort = listOf("createdAt,desc", "id,desc"))).hasSize(1000).isEqualTo(sqlOrder("created_at DESC, id DESC"))
        assertThat(RecordingStatementInspector.lastSelect()).contains("(l1_0.created_at,l1_0.id)<(?,?)")
    }

    @Test
    fun `mixed directions fall back to the OR chain, still visiting every row once`() {
        assertThat(walk(sort = listOf("price,desc"))).hasSize(1000).doesNotHaveDuplicates().isEqualTo(sqlOrder("price DESC, id ASC"))
        assertThat(RecordingStatementInspector.lastSelect()).contains("l1_0.price<?", " or ", "l1_0.price=?", "l1_0.id>?")
    }

    @Test
    fun `a filter and the cursor compose into one query`() {
        val visited = walk(sort = listOf("createdAt")) { path(Listing::status).equal("open") }

        assertThat(visited).isEqualTo(sqlOrder("created_at, id", where = "status = 'open'")).isNotEmpty()
        assertThat(visited.size).isLessThan(1000)
    }

    @Test
    fun `the cursor names its sort and holds the last row's key values, and the last page has none`() {
        val first = page(request(listOf("price,desc"), after = null))
        val last = first.items.last()
        assertThat(first.nextCursor!!.parts()).containsExactly("price:desc,id:asc", last.price.toString(), last.id.toString())

        val order = sqlOrder("price DESC, id ASC")
        val boundary = repository.findById(order[994]).get()
        val keys = spec.sortable.keysFor(SortRequest.parse(spec.sortable, listOf("price,desc")))
        val lastPage = page(request(listOf("price,desc"), after = keys.cursorOf(listOf(boundary.price, boundary.id)).value))
        assertThat(lastPage.items.map { it.id }).isEqualTo(order.takeLast(5))
        assertThat(lastPage.nextCursor).isNull()
    }

    @Test
    fun `a cursor from another sort is a client error naming both sorts`() {
        val fromPrice = page(request(listOf("price,desc"), after = null)).nextCursor!!

        assertThatThrownBy { page(request(listOf("createdAt"), after = fromPrice.value)) }
            .hasMessage("after: was made for sort 'price:desc,id:asc', not 'createdAt:asc,id:asc'")
    }

    @Test
    fun `the contract checks itself when the registry reads it`() {
        class Broken : SortSpec<Listing>(Listing::class, tieBreaker = "nope") {
            val id = sortable(KeysetKey.uuid("id"), Listing::id)
        }

        assertThatThrownBy { SortRegistry(listOf(Broken())).sortableFor(Listing::class) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("tie breaker 'nope' is not a sortable field")
        assertThat(spec.sortable.keys.map { it.direction }).containsOnly(Direction.ASC)
    }

    @Test
    fun `an unknown sort field is a 400 before any query runs`() {
        assertThatThrownBy { request(listOf("status"), after = null) }
            .hasMessageContaining("sort: 'status' is not sortable")
    }
}
