package io.github.siloverse.pagination.jdsl

import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ListingRepository : JpaRepository<Listing, UUID>, KotlinJdslJpqlExecutor
