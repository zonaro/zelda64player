package br.com.redclaw.zelda64player.tracker.assets

import br.com.redclaw.zelda64player.tracker.assets.dma.DmaEntry
import br.com.redclaw.zelda64player.tracker.assets.dma.DmaTableParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DmaTableParserTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun writeDmaTable(entries: List<DmaEntry>, terminator: Boolean = true): File {
        val file = tmp.newFile("rom.z64")
        val buf =
                ByteBuffer.allocate((entries.size + if (terminator) 1 else 0) * 16)
                        .order(ByteOrder.BIG_ENDIAN)
        for (e in entries) {
            buf.putInt(e.vromStart)
            buf.putInt(e.vromEnd)
            buf.putInt(e.romStart)
            buf.putInt(e.romEnd)
        }
        if (terminator) {
            buf.putInt(0)
            buf.putInt(0)
            buf.putInt(0)
            buf.putInt(0)
        }
        file.writeBytes(buf.array())
        return file
    }

    @Test
    fun `parse entries until terminator`() {
        val entries =
                listOf(
                        DmaEntry(0, 0x1000, 0x2000, 0x1000, 0),
                        DmaEntry(1, 0x2000, 0x3000, 0x2000, 0x2800),
                )
        val file = writeDmaTable(entries)
        val parser = DmaTableParser(file, 0L)
        val parsed = parser.parseEntries()
        assertEquals(2, parsed.size)
        assertEquals(0x1000, parsed[0].vromStart)
        assertFalse(parsed[0].isCompressed)
        assertTrue(parsed[1].isCompressed)
    }

    @Test
    fun `DmaEntry exists and sizes`() {
        val e = DmaEntry(0, 0x1000, 0x2000, 0x1000, 0)
        assertTrue(e.exists)
        assertFalse(e.isCompressed)
        assertEquals(0x1000, e.uncompressedSize)
        assertEquals(0x1000, e.compressedSize)

        val compressed = DmaEntry(1, 0x2000, 0x4000, 0x2000, 0x3000)
        assertTrue(compressed.isCompressed)
        assertEquals(0x2000, compressed.uncompressedSize)
        assertEquals(0x1000, compressed.compressedSize)
    }

    @Test
    fun `parse with table offset`() {
        // File: 0x100 bytes padding + DMA table
        val file = tmp.newFile("rom2.z64")
        val padding = ByteArray(0x100)
        val entries = listOf(DmaEntry(0, 0x1000, 0x2000, 0x1000, 0))
        val tableBuf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        for (e in entries) {
            tableBuf.putInt(e.vromStart)
            tableBuf.putInt(e.vromEnd)
            tableBuf.putInt(e.romStart)
            tableBuf.putInt(e.romEnd)
        }
        tableBuf.putInt(0)
        tableBuf.putInt(0)
        tableBuf.putInt(0)
        tableBuf.putInt(0)
        file.writeBytes(padding + tableBuf.array())
        val parser = DmaTableParser(file, 0x100L)
        val parsed = parser.parseEntries()
        assertEquals(1, parsed.size)
    }

    @Test
    fun `readEntryBytes returns correct slice`() {
        val file = tmp.newFile("rom3.z64")
        // Layout: DMA table at 0, then data at 0x1000
        val header = ByteArray(0x1000)
        // DMA entry 0: vrom 0x1000-0x1010, rom 0x1000-0x1010 (uncompressed, 16 bytes)
        val dma = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        dma.putInt(0x1000)
        dma.putInt(0x1010)
        dma.putInt(0x1000)
        dma.putInt(0)
        dma.putInt(0)
        dma.putInt(0)
        dma.putInt(0)
        dma.putInt(0)
        header[0] = dma.array()[0]
        System.arraycopy(dma.array(), 0, header, 0, 32)
        // Data at 0x1000
        val data = "Hello DMA!1234".toByteArray(Charsets.US_ASCII) // 14 bytes, pad to 16
        val full = header + ByteArray(0x1000 - header.size) + data + ByteArray(2)
        file.writeBytes(full)
        val parser = DmaTableParser(file, 0L)
        val entries = parser.parseEntries()
        assertEquals(1, entries.size)
        val bytes = parser.readEntryBytes(entries[0])
        assertEquals(16, bytes.size)
    }
}
