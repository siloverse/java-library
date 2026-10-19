package io.github.siloverse.pagination.spring.keyset

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Window
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ListingRepository : JpaRepository<Listing, UUID> {

    /** Spring Data keyset scrolling: the position carries the sort key values of the last row seen. */
    fun findFirst25ByOrderByCreatedAtAscIdAsc(position: ScrollPosition): Window<Listing>

    /** Offset page with the photos fetched in the same query: the collection-join trap. */
    @EntityGraph(attributePaths = ["photos"])
    fun findAllWithPhotosBy(pageable: Pageable): Page<Listing>
}
