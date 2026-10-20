package io.github.siloverse.pagination.spring.keyset

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/** Maps the fixture tables. `open` and default arguments because Hibernate needs a no-arg constructor and subclassing. */
@Entity
@Table(name = "listing")
open class Listing(
    @Id open var id: UUID = UUID.randomUUID(),
    open var title: String = "",
    @Column(name = "created_at") open var createdAt: OffsetDateTime = OffsetDateTime.MIN,
    @OneToMany(mappedBy = "listing", fetch = FetchType.LAZY) open var photos: MutableList<ListingPhoto> = mutableListOf(),
)

@Entity
@Table(name = "listing_photo")
open class ListingPhoto(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "listing_id") open var listing: Listing? = null,
    open var url: String = "",
)
