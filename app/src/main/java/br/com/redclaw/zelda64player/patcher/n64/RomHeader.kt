package br.com.redclaw.zelda64player.patcher.n64

import java.io.File
import java.io.RandomAccessFile

/**
 * Parsed N64 ROM header fields used to identify a base ROM.
 *
 * Offsets are given for the normalized big-endian `.z64` layout:
 * - `0x20..0x33` (20 bytes): internal title (null/space padded ASCII)
 * - `0x3B..0x3E` (4 bytes): game code, e.g. `CZLE` = OoT NTSC-U 1.0, `NSME` = MM NTSC-U 1.0
 * - `0x3F` (1 byte): version byte (0 = v1.0)
 *
 * @property gameCode four-character game code
 * @property versionByte header version byte
 * @property title internal ROM title (trimmed)
 */
data class RomHeader(
    val gameCode: String,
    val versionByte: Int,
    val title: String
) {
    companion object {
        private const val TITLE_OFFSET = 0x20
        private const val TITLE_LENGTH = 20
        private const val GAME_CODE_OFFSET = 0x3B
        private const val GAME_CODE_LENGTH = 4
        private const val VERSION_OFFSET = 0x3F

        /** Parse the header from an in-memory normalized z64 byte array. */
        fun fromNormalizedZ64(bytes: ByteArray): RomHeader {
            require(bytes.size > VERSION_OFFSET) {
                "Byte array too small to contain an N64 ROM header"
            }
            val title = readAscii(bytes, TITLE_OFFSET, TITLE_LENGTH)
            val gameCode = readAscii(bytes, GAME_CODE_OFFSET, GAME_CODE_LENGTH)
            val versionByte = bytes[VERSION_OFFSET].toInt() and 0xFF
            return RomHeader(gameCode, versionByte, title)
        }

        /** Parse the header from a normalized z64 file (only the needed ranges are read). */
        fun fromNormalizedZ64(file: File): RomHeader {
            RandomAccessFile(file, "r").use { raf ->
                val title = ByteArray(TITLE_LENGTH)
                raf.seek(TITLE_OFFSET.toLong())
                raf.readFully(title)

                val code = ByteArray(GAME_CODE_LENGTH)
                raf.seek(GAME_CODE_OFFSET.toLong())
                raf.readFully(code)

                raf.seek(VERSION_OFFSET.toLong())
                val versionByte = raf.read()

                return RomHeader(
                    readAscii(code, 0, GAME_CODE_LENGTH),
                    versionByte and 0xFF,
                    readAscii(title, 0, TITLE_LENGTH)
                )
            }
        }

        private fun readAscii(bytes: ByteArray, offset: Int, length: Int): String {
            val end = (offset + length).coerceAtMost(bytes.size)
            val sb = StringBuilder()
            for (i in offset until end) {
                val b = bytes[i].toInt() and 0xFF
                if (b == 0) break
                sb.append(b.toChar())
            }
            return sb.toString().trimEnd()
        }
    }
}
