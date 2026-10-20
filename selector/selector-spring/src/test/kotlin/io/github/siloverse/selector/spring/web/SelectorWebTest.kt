package io.github.siloverse.selector.spring.web

import io.github.siloverse.selector.core.selector.Selector
import io.github.siloverse.selector.core.spec.FieldType
import io.github.siloverse.selector.core.spec.SelectorSpec
import io.github.siloverse.selector.spring.config.SelectorConfiguration
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

/** The web path: query string → resolver → registry → controller, and the 400 at the door. Real Spring MVC, no Boot. */
class SelectorWebTest {

    class Order

    /** Engine-free specs are enough here; the web layer only needs keys and types. */
    class OrderStatusSpec : SelectorSpec<String>(Order::class, "order", "status", type = FieldType.STRING, arity = Arity.SINGLE)
    class OrderPriceLtSpec : SelectorSpec<Long>(Order::class, "order", "price", "lt", FieldType.LONG, Arity.SINGLE)

    @RestController
    class OrdersController {
        @GetMapping("/orders")
        fun orders(@Selectors(entity = Order::class) selectors: List<Selector>): List<String> =
            selectors.map { "${it.key}=${it.values.joinToString(",")}" }
    }

    @Configuration
    @EnableWebMvc
    @Import(SelectorConfiguration::class, OrdersController::class)
    open class App {
        @Bean open fun orderStatusSpec() = OrderStatusSpec()
        @Bean open fun orderPriceLtSpec() = OrderPriceLtSpec()
    }

    private val mvc: MockMvc = AnnotationConfigWebApplicationContext().let { context ->
        context.servletContext = MockServletContext()
        context.register(App::class.java)
        context.refresh()
        MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `the controller receives one selector per filter key, with the raw values`() {
        mvc.perform(get("/orders").param("filter.order.status", "open").param("filter.order.price.lt", "50"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value("filter.order.status=open"))
            .andExpect(jsonPath("$[1]").value("filter.order.price.lt=50"))
    }

    @Test
    fun `no filter params is an empty list`() {
        mvc.perform(get("/orders"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `paging and sort keys are not filters`() {
        mvc.perform(get("/orders").param("page", "1").param("size", "10").param("after", "x").param("sort", "price,desc").param("filter.order.status", "open"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `an unknown filter is a 400 at the door that lists the entity's filters`() {
        mvc.perform(get("/orders").param("filter.order.prize", "1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid filter"))
            .andExpect(jsonPath("$.parameter").value("filter.order.prize"))
            .andExpect(jsonPath("$.detail").value("filter.order.prize: unknown filter; filters for Order: [filter.order.price.lt, filter.order.status]"))
    }

    @Test
    fun `a malformed filter key is a 400`() {
        mvc.perform(get("/orders").param("filter.order", "1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.parameter").value("filter.order"))
    }
}
