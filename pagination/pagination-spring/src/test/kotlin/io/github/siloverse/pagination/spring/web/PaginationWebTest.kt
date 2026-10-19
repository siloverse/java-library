package io.github.siloverse.pagination.spring.web

import io.github.siloverse.pagination.core.keyset.Cursor
import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.offset.OffsetPage
import io.github.siloverse.pagination.core.offset.OffsetRequest
import io.github.siloverse.pagination.core.offset.Slice
import io.github.siloverse.pagination.core.policy.PaginationPolicy
import io.github.siloverse.pagination.spring.config.PaginationConfiguration
import io.github.siloverse.pagination.spring.extensions.toResponse
import io.github.siloverse.pagination.spring.response.KeysetPageResponse
import io.github.siloverse.pagination.spring.response.OffsetPageResponse
import io.github.siloverse.pagination.spring.response.SliceResponse

import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/**
 * The whole web path in one place: query string → resolver → policy → controller → JSON,
 * and the 400 when the policy says no. Runs a real Spring MVC context, no Boot.
 */
class PaginationWebTest {

    @RestController
    class ThingsController {
        private val things = (1..23).toList()

        @GetMapping("/offset")
        fun offset(request: OffsetRequest): OffsetPageResponse<Int> =
            OffsetPage(things.drop(request.offset.toInt()).take(request.size), request, things.size.toLong()).toResponse()

        @GetMapping("/slice")
        fun slice(request: OffsetRequest): SliceResponse<Int> =
            Slice.fromOverFetch(things.drop(request.offset.toInt()).take(request.size + 1), request).toResponse()

        @GetMapping("/keyset")
        fun keyset(request: KeysetRequest): KeysetPageResponse<Int> {
            val after = request.after?.parts()?.single()?.toInt() ?: Int.MIN_VALUE
            val rows = things.filter { it > after }.take(request.size + 1)
            return KeysetPage.fromOverFetch(rows, request) { Cursor.of(it.toString()) }.toResponse()
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(PaginationConfiguration::class, ThingsController::class)
    open class DefaultPolicyApp

    @Configuration
    @Import(DefaultPolicyApp::class)
    open class SmallPolicyApp {
        @Bean
        open fun paginationPolicy() = PaginationPolicy(defaultSize = 5, maxSize = 5)
    }

    private fun mockMvc(app: Class<*>): MockMvc {
        val context = AnnotationConfigWebApplicationContext().apply {
            servletContext = MockServletContext()
            register(app)
            refresh()
        }
        return MockMvcBuilders.webAppContextSetup(context).build()
    }

    private val mvc = mockMvc(DefaultPolicyApp::class.java)

    @Test
    fun `offset page comes back flat, with the default size when none is given`() {
        mvc.perform(get("/offset").param("page", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0]").value(21))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(23))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasPrevious").value(true))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.request").doesNotExist())
    }

    @Test
    fun `slice has no totals`() {
        mvc.perform(get("/slice").param("page", "0").param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(10))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.totalElements").doesNotExist())
    }

    @Test
    fun `keyset cursor is a plain string that walks to the next page`() {
        val first = mvc.perform(get("/keyset").param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0]").value(1))
            .andExpect(jsonPath("$.items[9]").value(10))
            .andExpect(jsonPath("$.nextCursor").exists())
            .andReturn()
        val cursor = Regex(""""nextCursor":"([^"]+)"""").find(first.response.contentAsString)!!.groupValues[1]

        mvc.perform(get("/keyset").param("size", "10").param("after", cursor))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0]").value(11))
            .andExpect(jsonPath("$.items[9]").value(20))
            .andExpect(jsonPath("$.hasNext").value(true))
    }

    @Test
    fun `size above the maximum is a 400 that names the parameter`() {
        mvc.perform(get("/offset").param("size", "101"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid pagination"))
            .andExpect(jsonPath("$.parameter").value("size"))
            .andExpect(jsonPath("$.detail").value("size: must be at most 100, was 101"))
    }

    @Test
    fun `a size that is not a number is a 400`() {
        mvc.perform(get("/offset").param("size", "ten"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.parameter").value("size"))
            .andExpect(jsonPath("$.detail").value("size: must be a whole number, was 'ten'"))
    }

    @Test
    fun `a made up cursor is a 400`() {
        mvc.perform(get("/keyset").param("after", "not a cursor!"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.parameter").value("after"))
    }

    @Test
    fun `a policy bean in the application replaces the default`() {
        val small = mockMvc(SmallPolicyApp::class.java)

        small.perform(get("/offset"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(5))
        small.perform(get("/offset").param("size", "6"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("size: must be at most 5, was 6"))
    }
}
