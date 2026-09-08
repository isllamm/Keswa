package keswa.core.common

/**
 * An i18n key identifying a user-facing error, never the message itself. Resolved to a localized
 * (Arabic-first) string in :core:ui — see docs/presentation-architecture.md §2. Domain errors carry
 * one of these instead of a message so a screen never renders raw exception text to the cashier.
 */
@JvmInline
value class ErrorKey(val value: String)

/** Something a use case or repository can fail with. Domain modules add their own; see :domain. */
interface AppError {
    val key: ErrorKey
}

/** Failures not specific to any one business rule. */
sealed class CommonError(override val key: ErrorKey) : AppError {
    data object NotFound : CommonError(ErrorKey("error.common.not_found"))
    data object Unauthorized : CommonError(ErrorKey("error.common.unauthorized"))
    data class Unexpected(val message: String?) : CommonError(ErrorKey("error.common.unexpected"))
}
