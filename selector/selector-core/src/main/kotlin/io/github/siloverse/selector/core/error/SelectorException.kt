package io.github.siloverse.selector.core.error

/**
 * The client asked for a filter or a sort the list does not allow.
 *
 * [parameter] names the query parameter at fault, so the web layer can answer with a 400
 * that says which one. A client error, never a server bug. Same shape as PaginationException.
 */
class SelectorException(val parameter: String, message: String) : RuntimeException("$parameter: $message")
