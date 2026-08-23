package br.com.redclaw.zelda64player.patcher.n64

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class ChecksumCalculatorTest {

    @Test
    fun crc32KnownVector() {
        // Standard CRC-32/ISO-HDLC check value for the ASCII string "123456789".
        assertEquals("cbf43926", ChecksumCalculator.crc32(ByteArrayInputStream("123456789".toByteArray())))
        assertEquals("cbf43926", ChecksumCalculator.crc32(tempFile("123456789")))
    }

    @Test
    fun md5KnownVector() {
        // Well-known MD5 of the ASCII string "123456789".
        assertEquals("25f9e794323b453885f5181f1b624d0b", ChecksumCalculator.md5(tempFile("123456789")))
    }

    @Test
    fun sha1KnownVector() {
        assertEquals("f7c3bc1d808e04732adf679965ccc34ca7ae3441", ChecksumCalculator.sha1(tempFile("123456789")))
    }

    @Test
    fun emptyInputCrc32IsZero() {
        assertEquals("00000000", ChecksumCalculator.crc32(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun toHexFormatsEightLowercaseDigits() {
        assertEquals("00000000", ChecksumCalculator.toHex(0L))
        assertEquals("ffffffff", ChecksumCalculator.toHex(0xFFFFFFFFL))
        assertEquals("cbf43926", ChecksumCalculator.toHex(0xCBF43926L))
    }

    private fun tempFile(content: String): File =
        File.createTempFile("csum", ".bin").apply { writeBytes(content.toByteArray()) }
}
