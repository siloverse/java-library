package io.github.siloverse.openapi.springdoc

import io.github.siloverse.pagination.core.keyset.KeysetKey
import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.offset.OffsetPage
import io.github.siloverse.pagination.core.offset.OffsetRequest
import io.github.siloverse.pagination.core.sort.Direction
import io.github.siloverse.pagination.core.sort.SortContract
import io.github.siloverse.pagination.core.sort.SortOrder
import io.github.siloverse.pagination.core.sort.SortableFields
import io.github.siloverse.pagination.spring.config.PaginationConfiguration
import io.github.siloverse.pagination.spring.response.KeysetPageResponse
import io.github.siloverse.pagination.spring.response.OffsetPageResponse
import io.github.siloverse.pagination.spring.util.toResponse
import io.github.siloverse.projector.core.Projectable
import io.github.siloverse.projector.core.Projection
import io.github.siloverse.projector.core.ProjectorManager
import io.github.siloverse.projector.core.SimpleProjector
import io.github.siloverse.projector.spring.config.ProjectorConfiguration
import io.github.siloverse.selector.core.selector.Selector
import io.github.siloverse.selector.core.spec.FieldType
import io.github.siloverse.selector.core.spec.SelectorSpec
import io.github.siloverse.selector.spring.config.SelectorConfiguration
import io.github.siloverse.selector.spring.web.Selectors
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

/** A small real application: one entity, its filters, its sort, its DTO's parts, and two list endpoints. */

class Listing(val id: UUID, val title: String, val status: String, val price: Long, val createdAt: OffsetDateTime)

data class ListingDto(
    val id: UUID,
    val title: String,
    @Projectable("owner") val owner: String? = null,
    @Projectable("photo-count", isDefault = true) val photoCount: Int? = null,
)

class ListingStatusSpec : SelectorSpec<String>(Listing::class, "listing", "status", type = FieldType.STRING, arity = Arity.SINGLE)
class ListingIdSpec : SelectorSpec<UUID>(Listing::class, "listing", "id", type = FieldType.UUID, arity = Arity.MULTI)
class ListingPriceLtSpec : SelectorSpec<Long>(Listing::class, "listing", "price", "lt", FieldType.LONG, Arity.SINGLE)
class ListingCreatedAfterSpec : SelectorSpec<OffsetDateTime>(Listing::class, "listing", "created-at", "after", FieldType.OFFSET_DATE_TIME, Arity.SINGLE)

class ListingSort : SortContract {
    override val entityType = Listing::class
    override val sortable = SortableFields(
        KeysetKey.offsetDateTime("createdAt"), KeysetKey.long("price"), KeysetKey.uuid("id"),
        tieBreaker = "id", defaultSort = listOf(SortOrder("createdAt", Direction.DESC)),
    )
}

class OwnerProjector : SimpleProjector<Listing, String>(Listing::class, "owner") {
    override fun project(entity: Listing): String = "owner of ${entity.title}"
}

class PhotoCountProjector : SimpleProjector<Listing, Int>(Listing::class, "photo-count") {
    override fun project(entity: Listing): Int = 3
}

@RestController
class ListingsController(private val projectorManager: ProjectorManager) {

    @GetMapping("/listings")
    fun listings(
        @Selectors(entity = Listing::class) selectors: List<Selector>,
        request: KeysetRequest,
        projection: Projection,
    ): KeysetPageResponse<ListingDto> = KeysetPage(emptyList<ListingDto>(), request, null).toResponse()

    @GetMapping("/listings/pages")
    fun pages(@Selectors(entity = Listing::class) selectors: List<Selector>, request: OffsetRequest): OffsetPageResponse<ListingDto> =
        OffsetPage(emptyList<ListingDto>(), request, 0).toResponse()

    /** A fixed-order list with no filters and no parts: nothing of ours should appear. */
    @GetMapping("/plain")
    fun plain(request: OffsetRequest): OffsetPageResponse<String> = OffsetPage(emptyList<String>(), request, 0).toResponse()
}

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(OpenApiConfiguration::class, PaginationConfiguration::class, SelectorConfiguration::class, ProjectorConfiguration::class, ListingsController::class)
open class ListingApp {
    @Bean open fun listingStatusSpec() = ListingStatusSpec()
    @Bean open fun listingIdSpec() = ListingIdSpec()
    @Bean open fun listingPriceLtSpec() = ListingPriceLtSpec()
    @Bean open fun listingCreatedAfterSpec() = ListingCreatedAfterSpec()
    @Bean open fun listingSort() = ListingSort()
    @Bean open fun ownerProjector() = OwnerProjector()
    @Bean open fun photoCountProjector() = PhotoCountProjector()
}
