package br.com.redclaw.zelda64player.data.local

import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaseRomRepositoryTest {

    private fun buildLogicalRom(size: Int = 4096): ByteArray {
        val bytes = ByteArray(size)
        for (i in bytes.indices) bytes[i] = ((i * 31 + 7) and 0xFF).toByte()
        bytes[0] = 0x80.toByte(); bytes[1] = 0x37.toByte(); bytes[2] = 0x12.toByte(); bytes[3] = 0x40.toByte()
        // Zero the full 20-byte title field first: leftover pattern bytes would
        // otherwise be parsed as part of the title.
        for (i in 0x20 until 0x34) bytes[i] = 0
        "THE LEGEND OF ZELDA".toByteArray().copyInto(bytes, 0x20)
        "CZLE".toByteArray().copyInto(bytes, 0x3B)
        bytes[0x3F] = 0x00
        return bytes
    }

    private fun swap16(input: ByteArray): ByteArray {
        val out = input.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val t = out[i]; out[i] = out[i + 1]; out[i + 1] = t; i += 2
        }
        return out
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
    fun scanRegistersValidZ64() {
        val env = newEnv()
        File(env.importDir, "oot.z64").writeBytes(buildLogicalRom())
        val roms = env.repo.scanAndRegister()
        assertEquals(1, roms.size)
        val rom = roms.first()
        assertEquals("CZLE", rom.gameCode)
        assertEquals(0, rom.versionByte)
        assertEquals("THE LEGEND OF ZELDA", RomHeader.fromNormalizedZ64(File(rom.path)).title)
        assertEquals(ChecksumCalculator.toHex(ChecksumCalculator.crc32Raw(File(rom.path))), rom.crc32)
        assertTrue(File(rom.path).exists())
    }

    @Test
    fun dedupeByCrc32AcrossFormats() {
        val env = newEnv()
        File(env.importDir, "oot.z64").writeBytes(buildLogicalRom())
        File(env.importDir, "oot.v64").writeBytes(swap16(buildLogicalRom()))
        val roms = env.repo.scanAndRegister()
        assertEquals(1, roms.size)
    }

    @Test
    fun registryPersistsAndReloads() {
        val env = newEnv()
        File(env.importDir, "oot.z64").writeBytes(buildLogicalRom())
        val first = env.repo.scanAndRegister()
        assertEquals(1, first.size)
        assertTrue(env.registry.exists())

        // A fresh repository instance over the SAME directories must reload the registry.
        val reloaded = BaseRomRepository(env.importDir, env.storageDir, env.registry).getAll()
        assertEquals(1, reloaded.size)
        assertEquals(first.first().crc32, reloaded.first().crc32)
        assertEquals(first.first().id, reloaded.first().id)
    }

    @Test
    fun findByCrc32AndGetById() {
        val env = newEnv()
        File(env.importDir, "oot.z64").writeBytes(buildLogicalRom())
        val rom = env.repo.scanAndRegister().first()
        assertEquals(rom.crc32, env.repo.findByCrc32(rom.crc32)?.crc32)
        assertEquals(rom.id, env.repo.getById(rom.id)?.id)
    }

    @Test
    fun migratesRomFileFromLegacyCacheDir() {
        val root = File.createTempFile("repo", "").also { it.delete() }.also { it.mkdirs() }
        val importDir = File(root, "import").also { it.mkdirs() }
        val legacyStorage = File(root, "legacy_cache").also { it.mkdirs() }
        val storageDir = File(root, "storage").also { it.mkdirs() }
        val registry = File(root, "base_roms.json")

        // Register a ROM normally, then simulate the old cache-dir layout:
        // move the stored file to the legacy dir and point the registry at it.
        val env = BaseRomRepository(importDir, storageDir, registry)
        File(importDir, "oot.z64").writeBytes(buildLogicalRom())
        val rom = env.scanAndRegister().first()
        val oldFile = File(rom.path)
        val legacyFile = File(legacyStorage, oldFile.name)
        oldFile.renameTo(legacyFile)
        org.json.JSONArray(registry.readText()).let { arr ->
            arr.getJSONObject(0).put("path", legacyFile.absolutePath)
            registry.writeText(arr.toString())
        }

        // A fresh instance with legacyStorageDirs must relocate the file and
        // update the registry path; the legacy copy must be gone afterwards.
        val migrated = BaseRomRepository(importDir, storageDir, registry, listOf(legacyStorage))
        val entries = migrated.getAll()
        assertEquals(1, entries.size)
        val newPath = entries.first().path
        assertTrue(newPath.startsWith(storageDir.absolutePath))
        assertTrue(File(newPath).exists())
        assertTrue(!legacyFile.exists())
    }

    @Test
    fun migrationKeepsEntriesWhenLegacyFileMissing() {
        val root = File.createTempFile("repo", "").also { it.delete() }.also { it.mkdirs() }
        val importDir = File(root, "import").also { it.mkdirs() }
        val legacyStorage = File(root, "legacy_cache").also { it.mkdirs() }
        val storageDir = File(root, "storage").also { it.mkdirs() }
        val registry = File(root, "base_roms.json")

        val env = BaseRomRepository(importDir, storageDir, registry)
        File(importDir, "oot.z64").writeBytes(buildLogicalRom())
        val rom = env.scanAndRegister().first()

        // File already gone everywhere (cache wiped): migration must leave the
        // entry untouched (scanAndRegister prunes it later).
        org.json.JSONArray(registry.readText()).let { arr ->
            arr.getJSONObject(0).put("path", File("/nonexistent/dir", File(rom.path).name).absolutePath)
            registry.writeText(arr.toString())
        }

        val repo = BaseRomRepository(importDir, storageDir, registry, listOf(legacyStorage))
        assertEquals(1, repo.getAll().size)
    }
}
