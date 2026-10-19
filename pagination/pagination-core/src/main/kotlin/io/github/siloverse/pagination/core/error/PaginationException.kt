package io.github.siloverse.pagination.core.error

/**
 * The client asked for a page in a way the policy does not allow.
 *
 * [parameter] names the query parameter at fault, so the web layer can answer with a
 * 400 that says which one. This is a client error, never a server bug.
 */
class PaginationException(val parameter: String, message: String) : RuntimeException("$parameter: $message")
