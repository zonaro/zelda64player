package br.com.redclaw.zelda64player.randomizer.patch

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Decodes a `.zpfz` (or multiworld `.zpf`) patch container into its constituent
 * `.zpf` blobs.
 *
 * A container is one or more **concatenated raw zlib streams** (RFC 1950). Each
 * stream inflates to exactly one `.zpf` blob (one per multiworld player). This
 * decoder walks the concatenated streams with [java.util.zip.Inflater], tracking
 * how many input bytes each stream consumed (`totalIn`) to locate the next
 * stream boundary.
 *
 * Licensing: clean-room implementation from the documented ZPF/ZPFZ format.
 */
object ZpfzDecoder {

    /**
     * Inflate every concatenated zlib stream in [patchFile], in order.
     *
     * @return one `.zpf` blob `ByteArray` per stream, preserving order.
     * @throws RandomizerPatchException.NoPatchStreams if the file yields zero
     *   streams.
     * @throws RandomizerPatchException.EmptyPatchStream if a stream inflates to
     *   zero bytes.
     * @throws RandomizerPatchException.CorruptPatchStream if a stream is not a
     *   valid zlib stream or ends prematurely.
     */
    fun decode(patchFile: File): List<ByteArray> {
        if (!patchFile.exists()) {
            throw RandomizerPatchException.PatchMissing(patchFile.absolutePath)
        }
        val raw = patchFile.readBytes()
        if (raw.isEmpty()) throw RandomizerPatchException.NoPatchStreams

        val blobs = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < raw.size) {
            val inflater = Inflater()
            inflater.setInput(raw, offset, raw.size - offset)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            try {
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    out.write(buffer, 0, n)
                    if (n == 0) {
                        // finished() is re-checked by the while condition; if we
                        // produced nothing and still need input, the stream is
                        // truncated/corrupt (all remaining bytes were provided).
                        if (inflater.needsInput() && !inflater.finished()) {
                            throw RandomizerPatchException.CorruptPatchStream(
                                "stream ended unexpectedly at input offset $offset"
                            )
                        }
                    }
                }
            } catch (e: DataFormatException) {
                inflater.end()
                throw RandomizerPatchException.CorruptPatchStream(e.message ?: "invalid zlib data")
            }

            val bytes = out.toByteArray()
            // Read totalIn BEFORE end(): after end() the native ZStreamRef is
            // nulled and getTotalIn() would throw NullPointerException.
            val consumed = inflater.totalIn.toInt()
            inflater.end()
            if (bytes.isEmpty()) throw RandomizerPatchException.EmptyPatchStream

            blobs.add(bytes)
            if (consumed <= 0) {
                throw RandomizerPatchException.CorruptPatchStream(
                    "zlib stream consumed no input at offset $offset"
                )
            }
            offset += consumed
        }

        if (blobs.isEmpty()) throw RandomizerPatchException.NoPatchStreams
        return blobs
    }
}
