package br.com.redclaw.zelda64player.randomizer.api

/**
 * Response returned by `POST /api/v2/seed/create`.
 *
 * @property id Server-assigned seed identifier used for status and patch queries.
 * @property version OoTR version that will generate this seed.
 * @property spoilers Whether a spoiler log was requested for this seed.
 */
data class SeedCreateResponse(
    val id: String,
    val version: String,
    val spoilers: Boolean
)

/**
 * Generation status returned by `GET /api/v2/seed/status`.
 *
 * @property status One of [STATUS_GENERATING], [STATUS_SUCCESS],
 *   [STATUS_GENERATED_WITH_LINK] or [STATUS_FAILED].
 * @property progress Generation progress in percent (0-100).
 * @property positionQueue Position in the generation queue, or `null` when the
 *   server does not report a queue position (e.g. actively generating).
 * @property maxWaitTime Estimated maximum wait in seconds, or `null` when the
 *   server does not provide an estimate.
 */
data class SeedStatus(
    val status: Int,
    val progress: Int,
    val positionQueue: Int? = null,
    val maxWaitTime: Int? = null
) {
    companion object {
        /** Seed is still being generated (not yet queued or actively building). */
        const val STATUS_GENERATING = 0

        /** Seed finished generating and is ready to download. */
        const val STATUS_SUCCESS = 1

        /** Seed finished generating and is served via a download link. */
        const val STATUS_GENERATED_WITH_LINK = 2

        /** Generation failed on the server. */
        const val STATUS_FAILED = 3
    }
}
