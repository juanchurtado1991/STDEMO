package com.example.stdemo.domain.model

/**
 * Failures the data layer reports to the rest of the app, independent of Retrofit, OkHttp or
 * Room types.
 *
 * Extends [Exception] so it can travel through Paging's `LoadState.Error`.
 */
sealed class DomainError(cause: Throwable? = null) : Exception(cause) {

    /** The device has no connectivity or the server could not be reached. */
    class Network(cause: Throwable? = null) : DomainError(cause)

    /**
     * The server answered with a non-successful HTTP status.
     *
     * @property code HTTP status code returned by the server.
     */
    class Http(val code: Int, cause: Throwable? = null) : DomainError(cause)

    /** The response body could not be parsed into the expected model. */
    class Parsing(cause: Throwable? = null) : DomainError(cause)

    /** Any failure that does not fit the other categories. */
    class Unknown(cause: Throwable? = null) : DomainError(cause)
}
