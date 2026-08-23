package br.com.redclaw.zelda64player.randomizer.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Polls seed generation status until completion.
 *
 * The loop calls [OotrApiClient.getSeedStatus] repeatedly, emitting progress
 * through [onProgress], until the server reports success or failure. A
 * [OotrApiException.RateLimited] triggers a longer back-off, and an overall
 * wall-clock [overallTimeoutMillis] caps the whole operation.
 *
 * The poller is fully cancellation-aware: it calls [kotlin.coroutines.ensureActive]
 * at the top of every iteration and uses the cancellable [delay], so cancelling
 * the enclosing coroutine (e.g. via `viewModelScope`) stops polling promptly.
 */
object SeedPoller {

    /**
     * @param client API client used for status checks.
     * @param apiKey User's OoTR API key (masked in logs/errors).
     * @param seedId Seed id returned by [OotrApiClient.createSeed].
     * @param onProgress Optional callback invoked with the latest progress
     *   percent and queue position (`null` when not queued).
     * @param pollIntervalMillis Delay between polls (default 2000ms).
     * @param rateLimitedDelayMillis Delay applied after a
     *   [OotrApiException.RateLimited] (default 5000ms).
     * @param overallTimeoutMillis Hard cap for the whole poll loop (default 10 min).
     * @return The terminal [SeedStatus] (status == [SeedStatus.STATUS_SUCCESS]
     *   or [SeedStatus.STATUS_GENERATED_WITH_LINK]).
     * @throws OotrApiException.GenerationFailed When the server reports failure.
     * @throws OotrApiException.GenerationTimeout When [overallTimeoutMillis] elapses.
     * @throws OotrApiException.NetworkError On unrecoverable network failure.
     */
    suspend fun pollUntilDone(
        client: OotrApiClient,
        apiKey: String,
        seedId: String,
        onProgress: suspend (progressPercent: Int, queuePosition: Int?) -> Unit = { _, _ -> },
        pollIntervalMillis: Long = 2000L,
        rateLimitedDelayMillis: Long = 5000L,
        overallTimeoutMillis: Long = 10 * 60 * 1000L
    ): SeedStatus {
        val deadline = System.currentTimeMillis() + overallTimeoutMillis
        while (true) {
            coroutineContext.ensureActive()

            val status = try {
                client.getSeedStatus(apiKey, seedId)
            } catch (e: OotrApiException.RateLimited) {
                delay(rateLimitedDelayMillis)
                continue
            }

            when (status.status) {
                SeedStatus.STATUS_SUCCESS,
                SeedStatus.STATUS_GENERATED_WITH_LINK -> return status

                SeedStatus.STATUS_FAILED -> throw OotrApiException.GenerationFailed(
                    "status=${status.status}, progress=${status.progress}"
                )

                else -> {
                    onProgress(status.progress, status.positionQueue)
                    if (System.currentTimeMillis() >= deadline) {
                        throw OotrApiException.GenerationTimeout
                    }
                    delay(pollIntervalMillis)
                }
            }
        }
    }
}
