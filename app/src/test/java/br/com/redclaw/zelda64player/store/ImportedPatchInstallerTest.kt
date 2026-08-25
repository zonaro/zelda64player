package br.com.redclaw.zelda64player.store

import android.content.ContextWrapper
import br.com.redclaw.zelda64player.data.local.BaseRomRepository
import br.com.redclaw.zelda64player.data.local.RegisterResult
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.UserHacksRepository
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.patcher.bps.BpsPatchEncoder
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import br.com.redclaw.zelda64player.repositories.Storage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests [ImportedPatchInstaller] end-to-end on the JVM (no emulator). A minimal
 * [ContextWrapper] provides the temp directories and string resources the
 * installer needs; the patcher's own test encoder builds valid BPS fixtures.
 */
class ImportedPatchInstallerTest {

    /** Minimal [android.content.Context] backed by temp directories. */
    private class TestContext(
        private val filesDir: File,
        private val cacheDir: File,
        private val externalFilesDir: File
    ) : ContextWrapper(null) {
        override fun getFilesDir(): File = filesDir
        override fun getCacheDir(): File = cacheDir
        override fun getExternalFilesDir(type: String?): File = externalFilesDir
        override fun getExternalCacheDir(): File = cacheDir
    }

    private fun newWorkspace(): Triple<File, File, File> {
        val root = File.createTempFile("ws", "").apply { delete(); mkdirs() }
        val storage = File(root, "storage").apply { mkdirs() }
        val cache = File(root, "cache").apply { mkdirs() }
        val files = File(root, "files").apply { mkdirs() }
        return Triple(storage, cache, files)
    }

    /** A dummy normalized z64 source with a CZLE header (so family = OoT). */
    private fun dummySource(): ByteArray {
        val bytes = ByteArray(1024) { (it % 251).toByte() }
        "CZLE".toByteArray().copyInto(bytes, 0x3B)
        bytes[0x3F] = 0
        return bytes
    }

    @Test
    fun installSuccessForBpsWithMatchingBaseRom() {
        val (storageDir, cacheDir, filesDir) = newWorkspace()
        val baseImportDir = File(storageDir, "base_import").apply { mkdirs() }
        val baseStorageDir = File(storageDir, "base_storage").apply { mkdirs() }
        val baseRepo = BaseRomRepository(
            baseImportDir,
            baseStorageDir,
            File(storageDir, "base_roms.json")
        )

        val source = dummySource()
        val sourceFile = File(baseStorageDir, "source.z64").apply { writeBytes(source) }
        val reg = baseRepo.registerNormalizedFile(sourceFile, "dummy")
        assertTrue(reg is RegisterResult.Success)

        val target = ByteArray(1024) { (it * 3).toByte() }
        val patchBytes = BpsPatchEncoder.encode(source, target)
        val patchFile = File(cacheDir, "my_hack.bps").apply { writeBytes(patchBytes) }

        val context = TestContext(filesDir, cacheDir, storageDir)
        val installer = ImportedPatchInstaller(
            context,
            baseRepo,
            InstalledHacksRepository(File(filesDir, "installed.json")),
            UserHacksRepository(File(filesDir, "user_hacks.json")),
            Storage(context)
        )

        val result = runBlocking { installer.install(patchFile, "my_hack") }
        assertTrue(result is ImportPatchSuccess)
        result as ImportPatchSuccess
        assertEquals("my_hack", result.hackId)
        assertEquals(OcarinaGame.OOT, result.family)

        val userHacks = UserHacksRepository(File(filesDir, "user_hacks.json"))
        assertEquals(result.hackId, userHacks.getById(result.hackId)?.id)
        assertTrue(Storage(context).rom(result.hackId).exists())
    }

    @Test
    fun noCompatibleRomWhenBaseMissing() {
        val (storageDir, cacheDir, filesDir) = newWorkspace()
        val baseRepo = BaseRomRepository(
            File(storageDir, "base_import").apply { mkdirs() },
            File(storageDir, "base_storage").apply { mkdirs() },
            File(storageDir, "base_roms.json")
        )

        val source = dummySource()
        val target = ByteArray(1024) { (it * 3).toByte() }
        val patchBytes = BpsPatchEncoder.encode(source, target)
        val expectedCrc = ChecksumCalculator.crc32(source)
        val patchFile = File(cacheDir, "orphan.bps").apply { writeBytes(patchBytes) }

        val context = TestContext(filesDir, cacheDir, storageDir)
        val installer = ImportedPatchInstaller(
            context,
            baseRepo,
            InstalledHacksRepository(File(filesDir, "installed.json")),
            UserHacksRepository(File(filesDir, "user_hacks.json")),
            Storage(context)
        )

        val result = runBlocking { installer.install(patchFile, "orphan") }
        assertTrue(result is ImportPatchNoCompatibleRom)
        result as ImportPatchNoCompatibleRom
        assertEquals(expectedCrc, result.expectedCrc32)
        assertTrue(result.foundCrc32s.isEmpty())
    }

    @Test
    fun unsupportedFormatForGarbageFile() {
        val (storageDir, cacheDir, filesDir) = newWorkspace()
        val baseRepo = BaseRomRepository(
            File(storageDir, "base_import").apply { mkdirs() },
            File(storageDir, "base_storage").apply { mkdirs() },
            File(storageDir, "base_roms.json")
        )
        val patchFile = File(cacheDir, "garbage.bps").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05))
        }

        val context = TestContext(filesDir, cacheDir, storageDir)
        val installer = ImportedPatchInstaller(
            context,
            baseRepo,
            InstalledHacksRepository(File(filesDir, "installed.json")),
            UserHacksRepository(File(filesDir, "user_hacks.json")),
            Storage(context)
        )

        val result = runBlocking { installer.install(patchFile, "garbage") }
        assertTrue(result is ImportPatchUnsupported)
    }

    @Test
    fun invalidPatchWhenPatchCorrupted() {
        val (storageDir, cacheDir, filesDir) = newWorkspace()
        val baseRepo = BaseRomRepository(
            File(storageDir, "base_import").apply { mkdirs() },
            File(storageDir, "base_storage").apply { mkdirs() },
            File(storageDir, "base_roms.json")
        )

        val source = dummySource()
        val sourceFile = File(File(storageDir, "base_storage"), "source.z64").apply { writeBytes(source) }
        val reg = baseRepo.registerNormalizedFile(sourceFile, "dummy")
        assertTrue(reg is RegisterResult.Success)

        val target = ByteArray(1024) { (it * 3).toByte() }
        val patchBytes = BpsPatchEncoder.encode(source, target)
        // Flip a body byte (not in the trailing 12-byte footer) so the embedded
        // patch CRC no longer verifies -> InvalidPatch.
        patchBytes[20] = (patchBytes[20] + 1).toByte()
        val patchFile = File(cacheDir, "corrupt.bps").apply { writeBytes(patchBytes) }

        val context = TestContext(filesDir, cacheDir, storageDir)
        val installer = ImportedPatchInstaller(
            context,
            baseRepo,
            InstalledHacksRepository(File(filesDir, "installed.json")),
            UserHacksRepository(File(filesDir, "user_hacks.json")),
            Storage(context)
        )

        val result = runBlocking { installer.install(patchFile, "corrupt") }
        assertTrue(result is ImportPatchInvalid)
        assertFalse(Storage(context).rom("corrupt").exists())
    }
}
