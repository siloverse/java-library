package io.github.siloverse.selector.spring.web

import io.github.siloverse.selector.core.error.SelectorException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * A bad filter is the client's fault. Answer 400 as an RFC 9457 problem detail and name the
 * query key, so the client can fix it without reading the server log.
 */
@RestControllerAdvice
class SelectorExceptionHandler {

    @ExceptionHandler(SelectorException::class)
    fun handle(exception: SelectorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message).apply {
            title = "Invalid filter"
            setProperty("parameter", exception.parameter)
        }
}
