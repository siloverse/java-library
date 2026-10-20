package io.github.siloverse.projector.spring.web

import io.github.siloverse.projector.core.ProjectorException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** A bad projection is the client's fault: 400 as an RFC 9457 problem detail naming `include` or `exclude`. */
@RestControllerAdvice
class ProjectorExceptionHandler {

    @ExceptionHandler(ProjectorException::class)
    fun handle(exception: ProjectorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message).apply {
            title = "Invalid projection"
            setProperty("parameter", exception.parameter)
        }
}
