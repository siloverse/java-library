package io.github.siloverse.selector.jdsl

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
    open var title: String = "",
    open var status: String = "",
    open var price: Long = 0,
    open var active: Boolean = false,
    @Column(name = "created_at") open var createdAt: OffsetDateTime = OffsetDateTime.MIN,
)
