package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchValidatorTest {

    @Test
    fun passesWhenCrcMatches() {
        val bytes = "hello world".toByteArray()
        val crc = ChecksumCalculator.crc32(bytes)
        val result = PatchValidator.validate(bytes, Checksums(crc32 = crc))
        assertTrue(result.isSuccess)
    }

    @Test
    fun failsWhenCrcMismatch() {
        val bytes = "hello world".toByteArray()
        val result = PatchValidator.validate(bytes, Checksums(crc32 = "deadbeef"))
        assertTrue(result.isFailure)
    }

    @Test
    fun passesWhenMd5ProvidedAndMatches() {
        val bytes = "hello world".toByteArray()
        val crc = ChecksumCalculator.crc32(bytes)
        val md5 = ChecksumCalculator.md5(bytes)
        val result = PatchValidator.validate(bytes, Checksums(crc32 = crc, md5 = md5))
        assertTrue(result.isSuccess)
    }

    @Test
    fun failsWhenMd5ProvidedButMismatch() {
        val bytes = "hello world".toByteArray()
        val crc = ChecksumCalculator.crc32(bytes)
        val result = PatchValidator.validate(
            bytes, Checksums(crc32 = crc, md5 = "deadbeefdeadbeefdeadbeefdeadbeef")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun md5IgnoredWhenAbsent() {
        val bytes = "hello world".toByteArray()
        val crc = ChecksumCalculator.crc32(bytes)
        val result = PatchValidator.validate(bytes, Checksums(crc32 = crc))
        assertTrue(result.isSuccess)
    }
}
