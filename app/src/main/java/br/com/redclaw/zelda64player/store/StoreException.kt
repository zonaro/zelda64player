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

    /** The patch could not be applied (bad/unrecognized patch or CRC failure). */
    class InvalidPatch(message: String) : StoreException(message)

    /**
     * No imported base ROM matches the patch's required source CRC32, so the
     * patch cannot be applied. [expectedCrc32] is the hex the patch wants;
     * [foundCrc32s] lists the CRC32s of every imported base ROM (for the UI).
     */
    class BaseRomMissing(val expectedCrc32: String, val foundCrc32s: List<String>) :
        StoreException("Base ROM missing (expected CRC32 $expectedCrc32)")

    /** Catch-all for unexpected failures (missing zip entry, IO, etc.). */
    class GenericError(message: String = "Download failed") : StoreException(message)
}
