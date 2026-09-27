package com.invokeil.shinigami.core.util

/** Typed result used across the app — never leaks raw exceptions to the UI. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Success) block(value)
        return this
    }

    fun onFailure(block: (AppError) -> Unit): AppResult<T> {
        if (this is Failure) block(error)
        return this
    }
}

/** Domain-level errors with human-readable messages. See MASTER SPEC §85. */
sealed class AppError(val userMessage: String, val detail: String? = null) :
    RuntimeException(userMessage) {
    data object NoInternet : AppError(
        "No internet connection. Offline commands still work.",
    )

    data object Timeout : AppError(
        "The AI provider took too long to respond.",
    )

    class Http(val code: Int, message: String, detail: String? = null) : AppError(
        when (code) {
            401, 403 -> "Your API key was rejected. Check the key in Providers."
            404 -> "Endpoint or model not found. Check the Base URL and model name."
            429 -> "Rate limit or quota exceeded on your provider."
            in 500..599 -> "The AI provider is having problems right now."
            else -> "The provider answered with HTTP $code."
        },
        detail,
    )

    data class Network(val reason: String) : AppError(
        "Unable to connect. Check the Base URL and your internet.",
        reason,
    )

    data class PermissionMissing(val what: String) : AppError(
        "Shini needs permission: $what",
    )

    data object Cancelled : AppError("Cancelled.")

    data class InvalidConfig(val what: String) : AppError(what)

    data class Unknown(val reason: String) : AppError(
        "Something went wrong. Try again.",
        reason,
    )
}

inline fun <T> runCatchingError(tag: String, block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (c: kotlinx.coroutines.CancellationException) {
    throw c
} catch (t: Throwable) {
    ShiniLog.e(tag, "operation failed", t)
    AppResult.Failure(t.toAppError())
}

fun Throwable.toAppError(): AppError = when {
    this is java.io.IOException && message?.contains("timeout", ignoreCase = true) == true ->
        AppError.Timeout
    this is java.net.SocketTimeoutException -> AppError.Timeout
    this is java.io.IOException -> AppError.Network(message ?: "io error")
    this is kotlinx.serialization.SerializationException ->
        AppError.Unknown("malformed response: ${Redactor.redact(message ?: "")}")
    else -> AppError.Unknown(Redactor.redact(message ?: this::class.java.simpleName))
}
