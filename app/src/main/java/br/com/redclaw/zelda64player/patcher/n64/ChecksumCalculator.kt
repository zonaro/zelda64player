package br.com.redclaw.zelda64player.patcher.n64

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Streaming checksum utilities for normalized ROM data.
 *
 * CRC32 uses [java.util.zip.CRC32] (IEEE 802.3, the algorithm referenced by the
 * BPS specification). MD5 and SHA-1 are provided for catalog cross-validation.
 * All digests are returned as lowercase hexadecimal strings.
 */
object ChecksumCalculator {

    /** CRC32 of a file, returned as an 8-digit lowercase hex string. */
    fun crc32(file: File): String = crc32(file.inputStream())

    /** CRC32 of a stream, returned as an 8-digit lowercase hex string. */
    fun crc32(input: InputStream): String {
        val crc = CRC32()
        input.use { stream ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) {
                crc.update(buf, 0, read)
            }
        }
        return toHex(crc.value)
    }

    /** Raw CRC32 (unsigned 32-bit long) of a file. Used internally for comparisons. */
    fun crc32Raw(file: File): Long {
        val crc = CRC32()
        file.inputStream().use { stream ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) {
                crc.update(buf, 0, read)
            }
        }
        return crc.value
    }

    /** MD5 of a file, returned as a 32-character lowercase hex string. */
    fun md5(file: File): String = digest(file, "MD5")

    /** SHA-1 of a file, returned as a 40-character lowercase hex string. */
    fun sha1(file: File): String = digest(file, "SHA-1")

    private fun digest(file: File, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        file.inputStream().use { stream ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) {
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Format an unsigned 32-bit value as an 8-digit lowercase hex string. */
    fun toHex(value: Long): String = "%08x".format(value and 0xFFFFFFFFL)
}
