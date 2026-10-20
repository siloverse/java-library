package io.github.siloverse.pagination.jdsl

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "listing")
open class Listing(
    @Id open var id: UUID = UUID.randomUUID(),
    open var status: String = "",
    open var price: Long = 0,
    @Column(name = "created_at") open var createdAt: OffsetDateTime = OffsetDateTime.MIN,
)
