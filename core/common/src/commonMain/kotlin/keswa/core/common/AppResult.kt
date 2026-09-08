package keswa.core.common

/**
 * Result of an operation that can fail with a typed [AppError]. Used across module boundaries
 * instead of exceptions as control flow (docs/architecture.md §4) — every use case and repository
 * method returns this rather than throwing for an expected failure.
 */
sealed class AppResult<out T> {
    data class Ok<out T>(val value: T) : AppResult<T>()
    data class Err(val error: AppError) : AppResult<Nothing>()

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Ok -> AppResult.Ok(transform(value))
    is AppResult.Err -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Ok -> transform(value)
    is AppResult.Err -> this
}

inline fun <T, R> AppResult<T>.fold(onOk: (T) -> R, onErr: (AppError) -> R): R = when (this) {
    is AppResult.Ok -> onOk(value)
    is AppResult.Err -> onErr(error)
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Ok)?.value

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Err)?.error
