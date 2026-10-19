package io.github.siloverse.pagination.spring.web

import io.github.siloverse.pagination.core.error.PaginationException

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * A bad page request is the client's fault. Answer 400 as an RFC 9457 problem detail
 * and name the parameter, so the client can fix it without reading the server log.
 */
@RestControllerAdvice
class PaginationExceptionHandler {

    @ExceptionHandler(PaginationException::class)
    fun handle(exception: PaginationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message).apply {
            title = "Invalid pagination"
            setProperty("parameter", exception.parameter)
        }
}
