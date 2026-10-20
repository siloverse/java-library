package io.github.siloverse.selector.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import io.github.siloverse.selector.core.definition.Field
import io.github.siloverse.selector.core.definition.FieldType
import io.github.siloverse.selector.core.definition.FilterDefinition
import io.github.siloverse.selector.core.definition.Operator
import io.github.siloverse.selector.core.filter.Selection
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
 * The adapter on a real database: query params in, rows out, and the SQL Hibernate ran
 * in between. Every expected set is computed with plain SQL so the test does not trust
 * the code it tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdslWhereTest {

    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var repository: ListingRepository
    private lateinit var jdbc: JdbcTemplate

    /** The list's contract, written once. `search` has no column: a strategy serves it. */
    private val id = Field("id", FieldType.UUID, Operator.EQ, Operator.IN)
    private val status = Field("status", FieldType.STRING, Operator.EQ, Operator.NE, Operator.IN)
    private val price = Field("price", FieldType.LONG, Operator.EQ, Operator.LT, Operator.LTE, Operator.GT, Operator.GTE)
    private val title = Field("title", FieldType.STRING, Operator.LIKE)
    private val active = Field("active", FieldType.BOOLEAN, Operator.EQ)
    private val search = Field("search", FieldType.STRING, Operator.EQ)
    private val definition = FilterDefinition(id, status, price, title, active, search)

    /** Each field bound once; the compiler checks Field<Long> against Listing::price. */
    private val bindings = JdslBindings<Listing>(definition) {
        bind(id, Listing::id)
        bind(status, Listing::status)
        bind(price, Listing::price)
        bind(title, Listing::title)
        bind(active, Listing::active)
        strategy(search) { condition -> path(Listing::title).like("%${condition.value}%") }
    }

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

    /** What a service method looks like: parse, then one query with the WHERE from the adapter. */
    private fun find(vararg params: Pair<String, String>): List<UUID> {
        val selection = Selection.parse(definition, params.groupBy({ it.first }, { it.second }))
        return repository.findAll {
            select(entity(Listing::class))
                .from(entity(Listing::class))
                .where(where(selection, bindings))
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
    fun `equals on a string`() {
        assertThat(find("status" to "open")).isEqualTo(sql("status = 'open'")).isNotEmpty()
    }

    @Test
    fun `conditions are ANDed, typed by their fields`() {
        assertThat(find("price.lt" to "10", "active" to "true")).isEqualTo(sql("price < 10 AND active"))
        assertThat(find("price.gte" to "990", "status.ne" to "draft")).isEqualTo(sql("price >= 990 AND status <> 'draft'"))
    }

    @Test
    fun `in with a list`() {
        val ids = sql("price IN (1, 2, 3)")

        assertThat(find("id.in" to ids.joinToString(","))).isEqualTo(ids)
        assertThat(find("status.in" to "open,closed")).isEqualTo(sql("status IN ('open', 'closed')"))
    }

    @Test
    fun `like passes the client's pattern through`() {
        assertThat(find("title.like" to "listing 1%")).isEqualTo(sql("title LIKE 'listing 1%'"))
    }

    @Test
    fun `a strategy serves a field that has no column`() {
        assertThat(find("search" to "ing 42")).isEqualTo(sql("title LIKE '%ing 42%'")).isNotEmpty()
    }

    @Test
    fun `values are bound as parameters, never written into the query`() {
        find("status" to "open", "price.lt" to "10")

        val sql = RecordingStatementInspector.lastSelect()
        assertThat(sql).contains("where (l1_0.status=?) and (l1_0.price<?)")
        assertThat(sql).doesNotContain("open", "10")
    }

    @Test
    fun `a field left unbound is refused when the list is written`() {
        assertThatThrownBy { JdslBindings<Listing>(definition) { bind(price, Listing::price) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("fields with no binding: [id, status, title, active, search]")
    }

    @Test
    fun `a field bound twice, or not part of the definition, is refused`() {
        assertThatThrownBy { JdslBindings<Listing>(definition) { bind(price, Listing::price); strategy(price) { and() } } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'price' is bound twice")
        val stranger = Field("price", FieldType.LONG, Operator.EQ)
        assertThatThrownBy { JdslBindings<Listing>(definition) { bind(stranger, Listing::price) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not part of this definition")
    }

    @Test
    fun `an empty selection gives no predicate`() {
        assertThat(with(Jpql.newInstance()) { where(Selection.NONE, bindings) }).isNull()
    }
}
