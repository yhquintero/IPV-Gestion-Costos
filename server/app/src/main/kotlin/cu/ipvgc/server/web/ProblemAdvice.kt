package cu.ipvgc.server.web

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ProblemAdvice {
    @ExceptionHandler(ApiException::class)
    fun api(ex: ApiException): ResponseEntity<Map<String, Any?>> =
        problem(ex.status, ex.code, ex.message, ex.extra)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any?>> =
        problem(HttpStatus.BAD_REQUEST, "invalid_request", "validation failed")

    @ExceptionHandler(AccessDeniedAppException::class)
    fun denied(ex: AccessDeniedAppException): ResponseEntity<Map<String, Any?>> =
        problem(HttpStatus.FORBIDDEN, "forbidden", ex.message ?: "forbidden")

    private fun problem(
        status: HttpStatus,
        code: String,
        detail: String,
        extra: Map<String, Any?> = emptyMap(),
    ): ResponseEntity<Map<String, Any?>> {
        val body = linkedMapOf<String, Any?>(
            "type" to "about:blank",
            "title" to status.reasonPhrase,
            "status" to status.value(),
            "code" to code,
            "detail" to detail,
        )
        extra.forEach { (k, v) -> body[k] = v }
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body)
    }
}

class AccessDeniedAppException(message: String) : RuntimeException(message)
