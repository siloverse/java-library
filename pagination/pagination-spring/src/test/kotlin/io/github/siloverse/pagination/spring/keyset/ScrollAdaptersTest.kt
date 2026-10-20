package io.github.siloverse.pagination.spring.keyset

import io.github.siloverse.pagination.core.error.PaginationException
import io.github.siloverse.pagination.core.keyset.Cursor
import io.github.siloverse.pagination.core.keyset.KeysetKey
import io.github.siloverse.pagination.core.keyset.KeysetKeys
import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.policy.PaginationPolicy
import io.github.siloverse.pagination.core.sort.Direction
import io.github.siloverse.pagination.core.sort.SortRequest
import io.github.siloverse.pagination.core.sort.SortableFields
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.query.FluentQuery
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import javax.sql.DataSource

/**
 * The Spring Data engine for keyset, proven on the real database. The request carries the
 * client's validated sort; the keys are built from it; the window becomes a page with a cursor.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScrollAdaptersTest {

    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var repository: ListingRepository
    private lateinit var jdbc: JdbcTemplate

    private val sortable = SortableFields(KeysetKey.offsetDateTime("createdAt"), KeysetKey.uuid("id"), tieBreaker = "id")
    private val policy = PaginationPolicy(defaultSize = 25, maxSize = 100)

    @BeforeAll
    fun start() {
        context = ListingJpaApp.start(ListingFixture.start())
        repository = context.getBean(ListingRepository::class.java)
        jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
    }

    @AfterAll
    fun stop() = context.close()

    private fun request(after: String?, sort: List<String>? = listOf("createdAt")): KeysetRequest =
        policy.keyset(after = after, size = null, sort = SortRequest.parse(sortable, sort))

    /** What a service method looks like with the adapter. */
    private fun page(after: String?, filter: Specification<Listing> = Specification.unrestricted()): KeysetPage<Listing> {
        val request = request(after)
        val keys = sortable.keysFor(request.sort)
        return repository
            .findBy(filter) { query: FluentQuery.FetchableFluentQuery<Listing> ->
                query.sortBy(keys.toSort()).limit(request.size).scroll(request.toScrollPosition(keys))
            }
            .toKeysetPage(request, keys)
    }

    @Test
    fun `walking the cursors through the adapter visits every row exactly once, in order`() {
        val visited = mutableListOf<UUID>()
        var page = page(after = null)
        while (true) {
            visited += page.items.map { it.id }
            page = page(after = (page.nextCursor ?: break).value)
        }

        assertThat(visited).hasSize(5000)
        assertThat(visited).doesNotHaveDuplicates()
        assertThat(visited).isEqualTo(ListingFixture.totalOrder(jdbc))
    }

    @Test
    fun `the cursor names its sort and holds the last row's values, as the database returned them`() {
        val first = page(after = null)
        val last = first.items.last()

        assertThat(first.nextCursor!!.parts()).containsExactly("createdAt:asc,id:asc", last.createdAt.toString(), last.id.toString())
    }

    @Test
    fun `a filter and the cursor compose into one query`() {
        val titlesStartingWith1 = Specification<Listing> { root, _, cb -> cb.like(root.get("title"), "listing 1%") }
        val expected = jdbc.queryForList(
            "SELECT id FROM listing WHERE title LIKE 'listing 1%' ORDER BY created_at, id", UUID::class.java,
        )

        val visited = mutableListOf<UUID>()
        var page = page(after = null, filter = titlesStartingWith1)
        while (true) {
            visited += page.items.map { it.id }
            page = page(after = (page.nextCursor ?: break).value, filter = titlesStartingWith1)
        }

        assertThat(visited).isEqualTo(expected)
        assertThat(visited.size).isLessThan(5000)
    }

    @Test
    fun `the last page has no cursor`() {
        val order = ListingFixture.totalOrder(jdbc)
        val boundary = repository.findById(order[4994]).get()
        val keys = sortable.keysFor(request(null).sort)

        val page = page(after = keys.cursorOf(listOf(boundary.createdAt, boundary.id)).value)

        assertThat(page.items.map { it.id }).isEqualTo(order.takeLast(5))
        assertThat(page.hasNext).isFalse()
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `a cursor made for another sort is a client error naming both sorts`() {
        val byIdDesc = sortable.keysFor(SortRequest.parse(sortable, listOf("id,desc")))
        val cursor = byIdDesc.cursorOf(listOf(UUID.randomUUID()))

        assertThatThrownBy { page(after = cursor.value) }
            .isInstanceOf(PaginationException::class.java)
            .hasMessage("after: was made for sort 'id:desc', not 'createdAt:asc,id:asc'")
    }

    @Test
    fun `a cursor whose part cannot be decoded is invalid`() {
        val keys = sortable.keysFor(request(null).sort)
        val request = KeysetRequest(after = Cursor.of(keys.signature, "not a date", UUID.randomUUID().toString()), size = 10, sort = request(null).sort)

        assertThatThrownBy { request.toScrollPosition(keys) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "after")
            .hasMessageContaining("not a valid cursor")
    }

    @Test
    fun `keys produce the matching sort`() {
        val keys = sortable.keysFor(SortRequest.parse(sortable, listOf("createdAt,desc")))
        val sort = keys.toSort()

        assertThat(sort.map { it.property }).containsExactly("createdAt", "id")
        assertThat(sort.getOrderFor("createdAt")!!.isDescending).isTrue()
        assertThat(sort.getOrderFor("id")!!.isAscending).isTrue()
        assertThat(KeysetKeys(KeysetKey.long("price", Direction.DESC)).toSort().first().isDescending).isTrue()
    }
}
