package br.com.redclaw.zelda64player.store

/**
 * Typed errors for the Hack Store download/validation pipeline. These cross the
 * boundary into the UI so it can show a specific, i18n'd message.
 */
sealed class StoreException(message: String) : Exception(message) {
    /** Network failure (HTTP error or no connectivity). */
    class NetworkError(message: String) : StoreException(message)

    /** Downloaded patch bytes did not match the catalog-declared checksum. */
    class ChecksumMismatch(val expected: String, val actual: String) :
        StoreException("Checksum mismatch (expected $expected, got $actual)")

    /** Catch-all for unexpected failures (missing zip entry, IO, etc.). */
    class GenericError(message: String = "Download failed") : StoreException(message)
}
