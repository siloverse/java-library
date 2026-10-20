package io.github.siloverse.projector.spring.web

import io.github.siloverse.projector.core.Projectable
import io.github.siloverse.projector.core.Projection
import io.github.siloverse.projector.core.Projector
import io.github.siloverse.projector.core.ProjectorManager
import io.github.siloverse.projector.core.SimpleProjector
import io.github.siloverse.projector.spring.config.ProjectorConfiguration
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

/** The web path: include/exclude → resolver → manager → JSON, and the 400. Real Spring MVC, no Boot. */
class ProjectorWebTest {

    class Order(val id: Int, val customer: String)
    data class OrderDto(
        val id: Int,
        @Projectable("customer") val customer: String? = null,
        @Projectable("item-count", isDefault = true) val itemCount: Int? = null,
    )

    class CustomerProjector : SimpleProjector<Order, String>(Order::class, "customer") {
        override fun project(entity: Order): String = entity.customer
    }

    class ItemCountProjector : Projector<Order, Int>(Order::class, "item-count") {
        override fun project(entities: List<Order>): Map<Order, Int> = entities.associateWith { it.id * 10 }
    }

    @RestController
    class OrdersController(private val manager: ProjectorManager) {
        private val orders = listOf(Order(1, "ada"), Order(2, "grace"))

        @GetMapping("/orders")
        fun orders(projection: Projection): List<OrderDto> =
            manager.apply(Order::class, orders, orders.map { OrderDto(it.id) }, projection)
    }

    @Configuration
    @EnableWebMvc
    @Import(ProjectorConfiguration::class, OrdersController::class)
    open class App {
        @Bean open fun customerProjector() = CustomerProjector()
        @Bean open fun itemCountProjector() = ItemCountProjector()
    }

    private val mvc: MockMvc = AnnotationConfigWebApplicationContext().let { context ->
        context.servletContext = MockServletContext()
        context.register(App::class.java)
        context.refresh()
        MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `defaults without parameters`() {
        mvc.perform(get("/orders"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].itemCount").value(10))
            .andExpect(jsonPath("$[0].customer").doesNotExist())
    }

    @Test
    fun `include switches a part on, exclude switches a default off`() {
        mvc.perform(get("/orders").param("include", "customer").param("exclude", "item-count"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[1].customer").value("grace"))
            .andExpect(jsonPath("$[1].itemCount").doesNotExist())
    }

    @Test
    fun `an unknown name is a 400 that lists the DTO's projections`() {
        mvc.perform(get("/orders").param("include", "cusomer"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid projection"))
            .andExpect(jsonPath("$.parameter").value("include"))
            .andExpect(jsonPath("$.detail").value("include: unknown projection 'cusomer'; projections of OrderDto: [customer, item-count]"))
    }

    @Test
    fun `an empty name is a 400`() {
        mvc.perform(get("/orders").param("include", "customer,"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("include: has an empty name"))
    }
}
