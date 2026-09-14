package com.alsoug.keswa.core.error

/** Base class for all domain errors. */
abstract class ErrorBase(
    override val message: String,
    override val cause: Throwable?
) : Exception(message, cause)

/**
 * Domain error hierarchy matching native implementation.
 * 
 * PCI DSS: Error messages never contain sensitive data (tokens, PAN, etc.).
 */
sealed class Error(
    override val message: String,
    override val cause: Throwable? = null,
    open val code: String? = null
) : ErrorBase(message, cause) {
    
    /**
     * Network-related errors (connectivity, timeouts, etc.)
     */
    data class NetworkError(
        override val message: String,
        override val code: String? = null
    ) : Error(message, code = code)
    
    /**
     * 401 Unauthorized - Session expired, requires re-authentication.
     */
    data class UnauthorizedAccess(
        val reason: String = "UnauthorizedAccess",
        override val code: String? = "UNAUTHORIZED"
    ) : Error(reason, code = code)
    
    /**
     * 403 Forbidden - User doesn't have permission.
     */
    data class ForbiddenAccess(
        val reason: String = "ForbiddenAccess",
        override val code: String? = "FORBIDDEN"
    ) : Error(reason, code = code)
    
    /**
     * Invalid data - serialization errors, validation failures.
     */
    data class InvalidData(
        val msg: String,
        override val code: String? = "INVALID_DATA"
    ) : Error(msg, code = code)
    
    /**
     * Unknown error - catch-all for unexpected errors.
     */
    data class UnknownError(
        val originalError: Throwable,
        override val code: String? = "UNKNOWN"
    ) : Error(
        originalError.message ?: "An unexpected error occurred",
        originalError,
        code = code
    )
    
    /**
     * No internet connection.
     */
    class InternetConnectionError : Error("Internet Connection Error", code = "NO_CONNECTION")

    /**
     * 404 Not Found - Resource doesn't exist.
     */
    data class NotFound(
        val msg: String,
        override val code: String? = "NOT_FOUND"
    ) : Error(msg, code = code)

    /**
     * 503 Service Unavailable - Service is temporarily down.
     */
    data class ServiceUnavailable(
        override val code: String? = "SERVICE_UNAVAILABLE"
    ) : Error("Service temporarily unavailable", code = code)

    /**
     * 5xx Server Error - Generic server-side failure.
     * Message is sanitized to avoid leaking raw response bodies.
     */
    data class ServerError(
        val statusCode: Int,
        override val code: String? = null
    ) : Error("Server error", code = code ?: statusCode.toString())

    /**
     * API envelope business error (`status=false`) with a displayable server message.
     * Native parity: fragments show [msg] via toast (e.g. LoginFragment invalid credentials).
     */
    data class ServerMessage(
        val msg: String,
        override val code: String? = null,
    ) : Error(msg, code = code)
}
