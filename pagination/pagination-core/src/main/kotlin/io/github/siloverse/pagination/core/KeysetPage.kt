package io.github.siloverse.pagination.core

/**
 * One page of results for a [KeysetRequest]. Carries the bookmark for the next page,
 * or null when this is the last page. Like [Slice] it never counts.
 */
data class KeysetPage<T>(
    val items: List<T>,
    val request: KeysetRequest,
    val nextCursor: Cursor?,
) {

    init {
        require(items.size <= request.size) {
            "page holds ${items.size} items but the request size is ${request.size}"
        }
        require(items.isNotEmpty() || nextCursor == null) {
            "an empty page cannot have a next cursor"
        }
    }

    val hasNext: Boolean
        get() = nextCursor != null

    companion object {

        /**
         * Builds a page from a query that fetched `size + 1` rows after the cursor.
         * If the extra row came back, a next page exists and the cursor points at the
         * last row that is kept. [cursorOf] turns a row into its bookmark; it is the
         * caller's knowledge of which columns the rows are sorted by.
         */
        fun <T> fromOverfetch(rows: List<T>, request: KeysetRequest, cursorOf: (T) -> Cursor): KeysetPage<T> {
            require(rows.size <= request.size + 1) {
                "fetched ${rows.size} rows but the overfetch limit is ${request.size + 1}"
            }
            val items = rows.take(request.size)
            val hasNext = rows.size > request.size
            return KeysetPage(
                items = items,
                request = request,
                nextCursor = if (hasNext) cursorOf(items.last()) else null,
            )
        }
    }
}
