package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator

/**
 * Validates downloaded patch bytes against the catalog-declared checksums.
 * CRC32 is mandatory; MD5 is checked only when the catalog provides it. SHA-1
 * is accepted in the model but not required for the download gate.
 *
 * Pure and side-effect free so it can be unit-tested directly without network.
 */
object PatchValidator {
    fun validate(bytes: ByteArray, checksums: Checksums): Result<Unit> = runCatching {
        val actualCrc = ChecksumCalculator.crc32(bytes)
        if (!actualCrc.equals(checksums.crc32, ignoreCase = true)) {
            throw StoreException.ChecksumMismatch(expected = checksums.crc32, actual = actualCrc)
        }
        checksums.md5?.let { expectedMd5 ->
            val actualMd5 = ChecksumCalculator.md5(bytes)
            if (!actualMd5.equals(expectedMd5, ignoreCase = true)) {
                throw StoreException.ChecksumMismatch(expected = expectedMd5, actual = actualMd5)
            }
        }
    }
}
