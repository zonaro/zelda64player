package br.com.redclaw.zelda64player.retroachievements.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/** One atomic disk record ties emulator bytes and achievement hit counts to the same ROM. */
internal data class RaSaveState(val core: ByteArray, val progress: ByteArray?, val hash: String)

internal object RaSaveStateCodec {
    private val magic = byteArrayOf(90, 54, 52, 82, 65, 83, 84, 49) // Z64RAST1
    private const val MAX_CORE = 128 * 1024 * 1024
    private const val MAX_PROGRESS = 16 * 1024 * 1024

    fun encode(state: RaSaveState): ByteArray {
        require(state.core.size in 1..MAX_CORE)
        val progress = state.progress ?: byteArrayOf()
        require(progress.size <= MAX_PROGRESS && state.hash.length <= 128)
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF(state.hash)
                out.writeInt(state.core.size)
                out.writeInt(progress.size)
                out.write(state.core)
                out.write(progress)
            }
        }.toByteArray()
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write(magic)
                out.writeLong(CRC32().apply { update(payload) }.value)
                out.write(payload)
            }
        }.toByteArray()
    }

    /** Legacy plain core states remain loadable, with achievement progress reset. */
    fun decode(bytes: ByteArray): RaSaveState {
        require(bytes.isNotEmpty())
        if (bytes.size < magic.size || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            require(bytes.size <= MAX_CORE)
            return RaSaveState(bytes, null, "")
        }
        return DataInputStream(ByteArrayInputStream(bytes, magic.size, bytes.size - magic.size)).use { input ->
            val checksum = input.readLong()
            val payload = input.readBytes()
            require(CRC32().apply { update(payload) }.value == checksum)
            DataInputStream(ByteArrayInputStream(payload)).use { data ->
                val hash = data.readUTF()
                val coreSize = data.readInt()
                val progressSize = data.readInt()
                require(hash.length <= 128 && coreSize in 1..MAX_CORE && progressSize in 0..MAX_PROGRESS)
                require(coreSize.toLong() + progressSize == data.available().toLong())
                val core = ByteArray(coreSize).also(data::readFully)
                val progress = if (progressSize == 0) null else ByteArray(progressSize).also(data::readFully)
                RaSaveState(core, progress, hash)
            }
        }
    }
}
