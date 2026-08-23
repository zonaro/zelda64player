package br.com.redclaw.zelda64player.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaseRomRepositoryRegisterTest {

    private fun buildLogicalRom(size: Int = 4096): ByteArray {
        val bytes = ByteArray(size)
        for (i in bytes.indices) bytes[i] = ((i * 31 + 7) and 0xFF).toByte()
        bytes[0] = 0x80.toByte(); bytes[1] = 0x37.toByte(); bytes[2] = 0x12.toByte(); bytes[3] = 0x40.toByte()
        for (i in 0x20 until 0x34) bytes[i] = 0
        "THE LEGEND OF ZELDA".toByteArray().copyInto(bytes, 0x20)
        "CZLE".toByteArray().copyInto(bytes, 0x3B)
        bytes[0x3F] = 0x00
        return bytes
    }

    private data class Env(val repo: BaseRomRepository, val importDir: File, val storageDir: File, val registry: File)

    private fun newEnv(): Env {
        val root = File.createTempFile("repo", "").also { it.delete() }.also { it.mkdirs() }
        val importDir = File(root, "import").also { it.mkdirs() }
        val storageDir = File(root, "storage").also { it.mkdirs() }
        val registry = File(root, "base_roms.json")
        return Env(BaseRomRepository(importDir, storageDir, registry), importDir, storageDir, registry)
    }

    @Test
    fun registerNormalizedFileSucceeds() {
        val env = newEnv()
        val file = File(env.storageDir, "normalized.z64")
        file.writeBytes(buildLogicalRom())

        val result = env.repo.registerNormalizedFile(file, "oot_source.z64")
        assertTrue(result is RegisterResult.Success)

        val rom = (result as RegisterResult.Success).rom
        assertEquals("CZLE", rom.gameCode)
        assertEquals(0, rom.versionByte)
        assertEquals("oot_source.z64", rom.sourceName)
        // File was consumed (moved to <crc32>.z64 in storageDir).
        assertFalse(file.exists())
        assertTrue(File(rom.path).exists())
        assertEquals(1, env.repo.getAll().size)
    }

    @Test
    fun registerDuplicateReturnsDuplicate() {
        val env = newEnv()
        val file1 = File(env.storageDir, "normalized1.z64")
        file1.writeBytes(buildLogicalRom())
        val first = env.repo.registerNormalizedFile(file1, "a.z64")
        assertTrue(first is RegisterResult.Success)

        val file2 = File(env.storageDir, "normalized2.z64")
        file2.writeBytes(buildLogicalRom())
        val second = env.repo.registerNormalizedFile(file2, "b.z64")
        assertTrue(second is RegisterResult.Duplicate)
        // The duplicate temp file is cleaned up.
        assertFalse(file2.exists())
        assertEquals(1, env.repo.getAll().size)
    }

    @Test
    fun registerTooSmallFileIsInvalid() {
        val env = newEnv()
        val file = File(env.storageDir, "tiny.z64")
        file.writeBytes(byteArrayOf(0x80.toByte(), 0x37.toByte(), 0x12.toByte(), 0x40.toByte()))
        val result = env.repo.registerNormalizedFile(file, "tiny.z64")
        assertTrue(result is RegisterResult.Invalid)
        assertFalse(file.exists())
        assertTrue(env.repo.getAll().isEmpty())
    }

    @Test
    fun deleteByIdRemovesFileAndRegistryEntry() {
        val env = newEnv()
        val file = File(env.storageDir, "normalized.z64")
        file.writeBytes(buildLogicalRom())
        val rom = (env.repo.registerNormalizedFile(file, "a.z64") as RegisterResult.Success).rom

        assertTrue(env.repo.deleteById(rom.id))
        assertFalse(File(rom.path).exists())
        assertTrue(env.repo.getAll().isEmpty())

        // Deleting an unknown id is a no-op returning false.
        assertFalse(env.repo.deleteById("does-not-exist"))
    }
}
