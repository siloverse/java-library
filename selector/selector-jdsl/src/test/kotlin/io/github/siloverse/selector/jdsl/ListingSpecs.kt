package io.github.siloverse.selector.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import io.github.siloverse.selector.core.spec.FieldType
import java.util.UUID

/** The filters of the listing list, one class each, as a silo would declare them. */

class ListingIdSpec : JdslMultiValueSpec<UUID>(Listing::class, "listing", "id", type = FieldType.UUID) {
    override fun Jpql.predicate(values: List<UUID>): Predicatable = path(Listing::id).`in`(values)
}

class ListingStatusSpec : JdslSingleValueSpec<String>(Listing::class, "listing", "status", type = FieldType.STRING) {
    override fun Jpql.predicate(value: String): Predicatable = path(Listing::status).equal(value)
}

class ListingStatusNotSpec : JdslSingleValueSpec<String>(Listing::class, "listing", "status", "not", FieldType.STRING) {
    override fun Jpql.predicate(value: String): Predicatable = path(Listing::status).notEqual(value)
}

class ListingStatusInSpec : JdslMultiValueSpec<String>(Listing::class, "listing", "status", "in", FieldType.STRING) {
    override fun Jpql.predicate(values: List<String>): Predicatable = path(Listing::status).`in`(values)
}

class ListingPriceLtSpec : JdslSingleValueSpec<Long>(Listing::class, "listing", "price", "lt", FieldType.LONG) {
    override fun Jpql.predicate(value: Long): Predicatable = path(Listing::price).lessThan(value)
}

class ListingPriceGteSpec : JdslSingleValueSpec<Long>(Listing::class, "listing", "price", "gte", FieldType.LONG) {
    override fun Jpql.predicate(value: Long): Predicatable = path(Listing::price).greaterThanOrEqualTo(value)
}

class ListingActiveSpec : JdslSingleValueSpec<Boolean>(Listing::class, "listing", "active", type = FieldType.BOOLEAN) {
    override fun Jpql.predicate(value: Boolean): Predicatable = path(Listing::active).equal(value)
}

/** A filter that is not a column: "contains" on the title. */
class ListingSearchSpec : JdslSingleValueSpec<String>(Listing::class, "listing", "search", type = FieldType.STRING) {
    override fun Jpql.predicate(value: String): Predicatable = path(Listing::title).like("%$value%")
}

/** A filter with a subquery: listings priced above the cheapest of their status group. */
class ListingNotCheapestSpec : JdslSingleValueSpec<Boolean>(Listing::class, "listing", "not-cheapest", type = FieldType.BOOLEAN) {
    override fun Jpql.predicate(value: Boolean): Predicatable {
        val other = entity(Listing::class, "other")
        val cheapest = select(min(other(Listing::price)))
            .from(other)
            .where(other(Listing::status).equal(path(Listing::status)))
            .asSubquery()
        return if (value) path(Listing::price).greaterThan(cheapest) else path(Listing::price).equal(cheapest)
    }
}
