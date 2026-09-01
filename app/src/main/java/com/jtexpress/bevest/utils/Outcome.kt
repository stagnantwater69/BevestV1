package com.jtexpress.bevest.utils

/**
 * Lightweight result wrapper for repository operations. Named [Outcome] to avoid a clash
 * with kotlin.Result.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) block(data)
    return this
}

inline fun <T> Outcome<T>.onFailure(block: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(error)
    return this
}

/** Domain-level error categories, kept UI-friendly. */
sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class Network(override val cause: Throwable? = null) :
        AppError("No connection. Check your network and try again.", cause)

    data class NotAuthorized(override val message: String = "You don't have access to this.") :
        AppError(message)

    data class NotFound(override val message: String = "That record no longer exists.") :
        AppError(message)

    data class Validation(override val message: String) : AppError(message)

    /**
     * The backend is not set up for this query yet — e.g. a Firestore composite index
     * has not been deployed. Distinct from [Unknown] because the fix is a deploy step,
     * not a retry.
     */
    data class Configuration(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    data class Unknown(override val cause: Throwable? = null) :
        AppError("Something went wrong. Please try again.", cause)
}
