package com.nido.music.core.common

/**
 * A generic wrapper for data operations that can succeed or fail.
 * Used across all layers to avoid throwing exceptions for expected failures.
 */
sealed interface NidoResult<out T> {
    data class Success<T>(val data: T) : NidoResult<T>
    data class Error(val exception: Throwable, val message: String? = null) : NidoResult<Nothing>
    data object Loading : NidoResult<Nothing>
}

/** Map the success value, propagate errors unchanged. */
inline fun <T, R> NidoResult<T>.map(transform: (T) -> R): NidoResult<R> = when (this) {
    is NidoResult.Success -> NidoResult.Success(transform(data))
    is NidoResult.Error -> this
    is NidoResult.Loading -> this
}

/** Unwrap or throw — use only when failure is truly unexpected. */
fun <T> NidoResult<T>.getOrThrow(): T = when (this) {
    is NidoResult.Success -> data
    is NidoResult.Error -> throw exception
    is NidoResult.Loading -> error("Result is still loading")
}

/** Unwrap or return null. */
fun <T> NidoResult<T>.getOrNull(): T? = when (this) {
    is NidoResult.Success -> data
    else -> null
}

/** Execute a suspending block and wrap in NidoResult. */
suspend fun <T> safeCall(block: suspend () -> T): NidoResult<T> = try {
    NidoResult.Success(block())
} catch (e: Exception) {
    NidoResult.Error(e, e.message)
}
