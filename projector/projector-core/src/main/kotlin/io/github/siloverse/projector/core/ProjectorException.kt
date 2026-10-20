package io.github.siloverse.projector.core

/**
 * The client asked for a projection the DTO does not offer. [parameter] is `include` or
 * `exclude`, so the web layer can answer with a 400 naming it. A client error, never a server bug.
 */
class ProjectorException(val parameter: String, message: String) : RuntimeException("$parameter: $message")
