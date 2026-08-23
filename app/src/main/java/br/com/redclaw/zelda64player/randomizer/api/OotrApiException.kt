package br.com.redclaw.zelda64player.randomizer.api

import java.io.IOException

/**
 * Typed exception hierarchy for the OoTR API client and seed poller.
 *
 * Every subclass carries a human-readable [message] that never contains the
 * API key. The key is always masked as "***" before being included in any
 * message or log line (see [OotrApiClient]).
 *
 * The hierarchy is `sealed` so exhaustive `when` expressions are possible.
 * [GenerationFailed] and [GenerationTimeout] are part of this same sealed
 * hierarchy (declared in OotrApiException.kt) so [SeedPoller] can throw them
 * while keeping the class sealed — Kotlin requires all sealed subclasses to
 * live in the same compilation unit.
 */
sealed class OotrApiException(message: String) : Exception(message) {

    /** Raised when an API call is attempted without a configured key. */
    object MissingApiKey : OotrApiException("API key not configured")

    /**
     * Raised on HTTP 400 - the server rejected the settings payload.
     *
     * @param serverMessage Optional error detail returned by the server.
     */
    data class InvalidSettings(val serverMessage: String?) :
        OotrApiException(serverMessage?.let { "Invalid settings: $it" } ?: "Invalid settings")

    /** Raised on HTTP 409 - the requested OoTR version is not available. */
    object VersionNotAvailable : OotrApiException("Requested OoTR version is not available")

    /** Raised on HTTP 423 - the generation queue is full. */
    object QueueFull : OotrApiException("Generation queue is full, try again later")

    /** Raised on HTTP 429 - rate limited by the server. */
    object RateLimited : OotrApiException("Rate limited by the server")

    /** Raised on HTTP 404 - the seed id does not exist. */
    object SeedNotFound : OotrApiException("Seed not found")

    /** Raised on HTTP 204 - the seed is still generating (no content yet). */
    object StillGenerating : OotrApiException("Seed is still generating")

    /**
     * Raised on any 5xx server error.
     *
     * @param code The HTTP status code returned by the server.
     */
    data class ServerError(val code: Int) : OotrApiException("Server error (HTTP $code)")

    /**
     * Raised when the underlying network call fails (no response / IO error).
     *
     * @param cause The underlying [IOException].
     */
    data class NetworkError(override val cause: IOException) :
        OotrApiException("Network error")

    /** Raised by [SeedPoller] when the server reports generation failure. */
    data class GenerationFailed(val detail: String) :
        OotrApiException("Seed generation failed: $detail")

    /** Raised by [SeedPoller] when generation exceeds the overall timeout. */
    object GenerationTimeout : OotrApiException("Seed generation timed out")
}
