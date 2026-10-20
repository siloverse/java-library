package io.github.siloverse.selector.spring.web

import io.github.siloverse.selector.core.definition.Field
import io.github.siloverse.selector.core.definition.FieldType
import io.github.siloverse.selector.core.definition.FilterDefinition
import io.github.siloverse.selector.core.definition.Operator
import io.github.siloverse.selector.core.filter.Selection
import io.github.siloverse.selector.spring.config.SelectorConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

/** The web path: query string → resolver → parser → controller, and the 400 when the list says no. Real Spring MVC, no Boot. */
class SelectorWebTest {

    @RestController
    class ListingsController {
        @GetMapping("/listings")
        fun listings(@Filtered("listingFilters") selection: Selection): List<String> =
            selection.conditions.map { it.toString() }

        @GetMapping("/broken")
        fun broken(selection: Selection): List<String> = emptyList()
    }

    @Configuration
    @EnableWebMvc
    @Import(SelectorConfiguration::class, ListingsController::class)
    open class App {
        @Bean open fun listingFilters() = FilterDefinition(
            Field("status", FieldType.STRING, Operator.EQ, Operator.IN),
            Field("price", FieldType.LONG, Operator.LT, Operator.GTE),
        )
    }

    private val mvc: MockMvc = AnnotationConfigWebApplicationContext().let { context ->
        context.servletContext = MockServletContext()
        context.register(App::class.java)
        context.refresh()
        MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `the controller receives the parsed, typed conditions`() {
        mvc.perform(get("/listings").param("status", "open").param("price.lt", "50"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value("status.eq=open"))
            .andExpect(jsonPath("$[1]").value("price.lt=50"))
    }

    @Test
    fun `no filter params is an empty selection`() {
        mvc.perform(get("/listings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `paging and sort keys pass through untouched`() {
        mvc.perform(get("/listings").param("page", "1").param("size", "10").param("after", "x").param("sort", "price,desc").param("status", "open"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `an unknown key is a 400 that names it`() {
        mvc.perform(get("/listings").param("prodcut", "1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid filter"))
            .andExpect(jsonPath("$.parameter").value("prodcut"))
            .andExpect(jsonPath("$.detail").value("prodcut: unknown filter field 'prodcut'; fields: [status, price]"))
    }

    @Test
    fun `a bad value is a 400 with the type's message`() {
        mvc.perform(get("/listings").param("price.lt", "cheap"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.parameter").value("price.lt"))
            .andExpect(jsonPath("$.detail").value("price.lt: 'cheap' is not a valid long"))
    }

    @Test
    fun `a Selection parameter without the annotation is a programming error that says so`() {
        assertThatThrownBy { mvc.perform(get("/broken")) }
            .hasRootCauseInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("is a Selection but has no @Filtered")
    }
}
