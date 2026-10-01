package cu.ipvgc.server.web

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val extra: Map<String, Any?> = emptyMap(),
) : RuntimeException(message) {
    companion object {
        fun unauthorized(detail: String = "invalid credentials"): ApiException =
            ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", detail)

        fun forbidden(code: String, detail: String): ApiException =
            ApiException(HttpStatus.FORBIDDEN, code, detail)

        fun notFound(entity: String): ApiException =
            ApiException(HttpStatus.NOT_FOUND, "not_found", "$entity not found")

        fun conflict(code: String, detail: String): ApiException =
            ApiException(HttpStatus.CONFLICT, code, detail)

        fun unprocessable(code: String, detail: String, extra: Map<String, Any?> = emptyMap()): ApiException =
            ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail, extra)

        fun badRequest(code: String, detail: String): ApiException =
            ApiException(HttpStatus.BAD_REQUEST, code, detail)

        fun tooManyRequests(detail: String = "too many login attempts"): ApiException =
            ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", detail)
    }
}
