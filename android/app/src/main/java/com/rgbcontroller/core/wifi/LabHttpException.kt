package com.rgbcontroller.core.wifi

sealed class LabHttpException(override val message: String) : Exception(message) {
    data class BadRequest(val statusCode: Int = 400, override val message: String) : LabHttpException(message)
    data class Unauthorized(val statusCode: Int = 401, override val message: String) : LabHttpException(message)
    data class Forbidden(val statusCode: Int = 403, override val message: String) : LabHttpException(message)
    data class Conflict(val statusCode: Int = 409, override val message: String) : LabHttpException(message)
    data class RateLimited(val statusCode: Int = 429, override val message: String) : LabHttpException(message)
    data class ServerError(val statusCode: Int, override val message: String) : LabHttpException(message)
    data class NetworkUnavailable(override val message: String) : LabHttpException(message)
    data class ProtocolError(val statusCode: Int, override val message: String) : LabHttpException(message)

    companion object {
        fun fromStatusCode(code: Int, rawReason: String): LabHttpException {
            val details = if (rawReason.isNotBlank()) rawReason else "HTTP $code"
            return when (code) {
                400 -> BadRequest(code, details)
                401 -> Unauthorized(code, details)
                403 -> Forbidden(code, details)
                409 -> Conflict(code, details)
                429 -> RateLimited(code, details)
                in 500..599 -> ServerError(code, details)
                else -> ProtocolError(code, details)
            }
        }
    }
}
