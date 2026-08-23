package br.com.redclaw.zelda64player.patcher.bps

import br.com.redclaw.zelda64player.patcher.PatcherException.PatchFormatError
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Streaming parser for the BPS (beat) patch format, implemented clean-room from
 * the public specification (byuu, public domain). No GPL source was read.
 *
 * File layout:
 * ```
 * string "BPS1"
 * number source-size
 * number target-size
 * number metadata-size
 * string metadata[metadata-size]
 * repeat { command }
 * uint32 source-checksum
 * uint32 target-checksum
 * uint32 patch-checksum
 * ```
 *
 * A command's first decoded varint packs the action in the low 2 bits and
 * `(length - 1)` in the high bits: `action = data & 3`, `length = (data >> 2) + 1`.
 */
object BpsParser {

    const val MAGIC = "BPS1"

    /** Parsed patch header (everything before the command stream). */
    data class BpsHeader(
        val sourceSize: Long,
        val targetSize: Long,
        val metadataSize: Long,
        val metadata: ByteArray
    )

    /** A single patch command. Copy offsets are signed relative adjustments. */
    sealed class BpsCommand {
        /** Copy `length` bytes from source[outputOffset] to target[outputOffset]. */
        data class SourceRead(val length: Long) : BpsCommand()

        /** Emit `length` literal bytes stored inline in the patch. */
        data class TargetRead(val data: ByteArray) : BpsCommand()

        /** Copy `length` bytes from the source file at a relative offset. */
        data class SourceCopy(val length: Long, val offset: Long) : BpsCommand()

        /** Copy `length` bytes from already-written target at a relative offset. */
        data class TargetCopy(val length: Long, val offset: Long) : BpsCommand()
    }

    /** Patch footer checksums (raw unsigned 32-bit values). */
    data class BpsFooter(
        val sourceCrc32: Long,
        val targetCrc32: Long,
        val patchCrc32: Long
    )

    /** Read and validate the header, leaving [input] positioned at the first command. */
    fun readHeader(input: InputStream): BpsHeader {
        val magic = ByteArray(4)
        if (input.read(magic) != 4) throw PatchFormatError("Patch too small to contain a header")
        if (!magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) {
            throw PatchFormatError("Missing 'BPS1' magic (got '${magic.toString(Charsets.US_ASCII)}')")
        }
        val sourceSize = VarInt.decode(input)
        val targetSize = VarInt.decode(input)
        val metadataSize = VarInt.decode(input)
        val metadata = ByteArray(metadataSize.toInt())
        if (metadataSize > 0 && input.read(metadata) != metadataSize.toInt()) {
            throw PatchFormatError("Unexpected end of stream while reading metadata")
        }
        return BpsHeader(sourceSize, targetSize, metadataSize, metadata)
    }

    /** Decode the next command from [input]. */
    fun readCommand(input: InputStream): BpsCommand {
        val data = VarInt.decode(input)
        val action = (data and 3L).toInt()
        val length = (data ushr 2) + 1L
        return when (action) {
            0 -> BpsCommand.SourceRead(length)
            1 -> {
                val bytes = ByteArray(length.toInt())
                if (input.read(bytes) != length.toInt()) {
                    throw PatchFormatError("Unexpected end of stream while reading TargetRead data")
                }
                BpsCommand.TargetRead(bytes)
            }
            2 -> {
                val encoded = VarInt.decode(input)
                val offset = decodeSignedOffset(encoded)
                BpsCommand.SourceCopy(length, offset)
            }
            3 -> {
                val encoded = VarInt.decode(input)
                val offset = decodeSignedOffset(encoded)
                BpsCommand.TargetCopy(length, offset)
            }
            else -> throw PatchFormatError("Unknown BPS action: $action")
        }
    }

    /** Read the 12-byte footer (three uint32 little-endian checksums). */
    fun readFooter(input: InputStream): BpsFooter {
        val sourceCrc32 = readUint32Le(input)
        val targetCrc32 = readUint32Le(input)
        val patchCrc32 = readUint32Le(input)
        return BpsFooter(sourceCrc32, targetCrc32, patchCrc32)
    }

    /** Decode a relative offset varint: low bit = sign (1 = negative), high bits = abs value. */
    internal fun decodeSignedOffset(encoded: Long): Long {
        val magnitude = encoded ushr 1
        return if (encoded and 1L != 0L) -magnitude else magnitude
    }

    /** Encode a signed relative offset for patch generation (used by tests/tooling). */
    internal fun encodeSignedOffset(offset: Long): Long {
        val magnitude = kotlin.math.abs(offset)
        return (magnitude shl 1) or (if (offset < 0) 1L else 0L)
    }

    private fun readUint32Le(input: InputStream): Long {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            throw PatchFormatError("Unexpected end of stream while reading footer checksum")
        }
        return (b0.toLong() and 0xFF) or
            ((b1.toLong() and 0xFF) shl 8) or
            ((b2.toLong() and 0xFF) shl 16) or
            ((b3.toLong() and 0xFF) shl 24)
    }
}
