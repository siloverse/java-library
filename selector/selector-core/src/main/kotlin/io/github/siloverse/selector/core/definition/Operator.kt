package io.github.siloverse.selector.core.definition

/** The comparisons a client may ask for. The [suffix] is what appears in the query key: `price.lt=50`. */
enum class Operator(val suffix: String) {
    EQ("eq"),
    NE("ne"),
    LT("lt"),
    LTE("lte"),
    GT("gt"),
    GTE("gte"),
    IN("in"),
    LIKE("like"),
    ;

    companion object {
        fun fromSuffix(suffix: String): Operator? = entries.firstOrNull { it.suffix == suffix }
    }
}
