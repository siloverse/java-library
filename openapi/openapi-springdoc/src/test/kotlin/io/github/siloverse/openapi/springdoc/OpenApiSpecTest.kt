package io.github.siloverse.openapi.springdoc

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.nio.file.Files

/** The spec a real Spring Boot app produces for a list endpoint that uses all three libraries. */
@SpringBootTest(classes = [ListingApp::class])
@AutoConfigureMockMvc
class OpenApiSpecTest(@Autowired private val mockMvc: MockMvc) {

    private val spec: String by lazy { mockMvc.perform(get("/v3/api-docs")).andReturn().response.contentAsString }

    private fun parameters(path: String): List<Map<String, Any?>> =
        JsonPath.read(spec, "$.paths['$path'].get.parameters")

    private fun parameter(path: String, name: String): Map<String, Any?> =
        parameters(path).single { it["name"] == name }

    @Test
    fun `every filter of the entity is a typed query parameter named by its key`() {
        val names = parameters("/listings").map { it["name"] }
        assertThat(names).contains("filter.listing.created-at.after", "filter.listing.id", "filter.listing.price.lt", "filter.listing.status")

        val id = parameter("/listings", "filter.listing.id")
        assertThat(JsonPath.read<String>(id, "$.schema.type")).isEqualTo("array")
        assertThat(JsonPath.read<String>(id, "$.schema.items.format")).isEqualTo("uuid")
        assertThat(JsonPath.read<String>(parameter("/listings", "filter.listing.price.lt"), "$.schema.format")).isEqualTo("int64")
        assertThat(JsonPath.read<String>(parameter("/listings", "filter.listing.created-at.after"), "$.schema.format")).isEqualTo("date-time")
    }

    @Test
    fun `keyset paging documents after, size and sort with the entity's sortable fields`() {
        val names = parameters("/listings").map { it["name"] }
        assertThat(names).contains("after", "size", "sort").doesNotContain("page")

        val sort = parameter("/listings", "sort")
        assertThat(sort["description"] as String).contains("[createdAt, price, id]", "Default: createdAt,desc", "`id` is always appended")
        assertThat(JsonPath.read<Int>(parameter("/listings", "size"), "$.schema.maximum")).isEqualTo(100)
        assertThat(JsonPath.read<Int>(parameter("/listings", "size"), "$.schema.default")).isEqualTo(20)
    }

    @Test
    fun `offset paging documents page and size`() {
        assertThat(parameters("/listings/pages").map { it["name"] }).contains("page", "size", "sort").doesNotContain("after")
    }

    @Test
    fun `include and exclude are enums of the DTO's parts`() {
        val include = parameter("/listings", "include")
        assertThat(JsonPath.read<List<String>>(include, "$.schema.items.enum")).containsExactly("owner", "photo-count")
        assertThat(include["description"] as String).contains("On by default: [photo-count]")
        assertThat(JsonPath.read<List<String>>(parameter("/listings", "exclude"), "$.schema.items.enum")).containsExactly("owner", "photo-count")
    }

    @Test
    fun `our resolved parameters do not leak as springdoc's guesses`() {
        val names = parameters("/listings").map { it["name"] as String }
        assertThat(names).doesNotContain("selectors", "request", "projection")
        assertThat(names.filter { it.contains("request") || it.contains("selectors") }).isEmpty()
    }

    @Test
    fun `a fixed-order list without filters or parts gets only page and size`() {
        assertThat(parameters("/plain").map { it["name"] }).containsExactlyInAnyOrder("page", "size")
    }

    @Test
    fun `the spec can be written as the committed contract, and reports drift`() {
        val target = Files.createTempDirectory("spec").resolve("openapi-spec.yaml")

        assertThat(OpenApiSpec.write(mockMvc, target)).isTrue()
        assertThat(Files.readString(target)).contains("openapi: 3.", "/listings:", "filter.listing.status")
        assertThat(OpenApiSpec.write(mockMvc, target)).isFalse()
    }
}
