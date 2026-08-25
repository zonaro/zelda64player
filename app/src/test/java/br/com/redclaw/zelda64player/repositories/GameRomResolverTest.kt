package br.com.redclaw.zelda64player.repositories

import br.com.redclaw.zelda64player.data.model.BaseRom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * Tests the pure resolution core [GameRomResolver.resolveRomFile]: vanilla ids map
 * to the registered base ROM file, unknown vanilla ids and missing files resolve to
 * null, and non-vanilla ids fall back to the patched-ROM path factory.
 */
class GameRomResolverTest {

    private fun baseRom(id: String, path: String) = BaseRom(
        id = id,
        displayName = "ROM $id",
        path = path,
        gameCode = "CZLE",
        versionByte = 0,
        sizeBytes = 1,
        crc32 = id,
        md5 = null,
        sha1 = null
    )

    private fun resolverWith(
        baseRomLookup: (String) -> BaseRom?,
        storageRom: (String) -> File
    ) = { hackId: String -> GameRomResolver.resolveRomFile(hackId, baseRomLookup, storageRom) }

    @Test
    fun vanillaIdResolvesToRegisteredBaseRomFile() {
        val file = File.createTempFile("base", ".z64").apply { writeText("rom") }
        val resolve = resolverWith(
            baseRomLookup = { if (it == "ABCDEF01") baseRom("ABCDEF01", file.absolutePath) else null },
            storageRom = { File("/store/rom_$it") }
        )
        val result = resolve("vanilla_ABCDEF01")
        assertNotNull(result)
        assertEquals(file.absolutePath, result?.absolutePath)
    }

    @Test
    fun unknownVanillaIdResolvesToMissing() {
        val resolve = resolverWith(
            baseRomLookup = { null },
            storageRom = { File("/store/rom_$it") }
        )
        assertNull(resolve("vanilla_UNKNOWN"))
    }

    @Test
    fun vanillaIdWithMissingFileResolvesToMissing() {
        // Registry entry exists but the file was deleted from disk.
        val resolve = resolverWith(
            baseRomLookup = { baseRom(it, "/no/such/dir/$it.z64") },
            storageRom = { File("/store/rom_$it") }
        )
        assertNull(resolve("vanilla_ABCDEF01"))
    }

    @Test
    fun nonVanillaIdFallsBackToStorageRomPath() {
        val resolve = resolverWith(
            baseRomLookup = { null },
            storageRom = { File("/store/rom_$it") }
        )
        val result = resolve("some_hack_id")
        assertNotNull(result)
        assertEquals("/store/rom_some_hack_id", result?.absolutePath)
    }

    @Test
    fun nonVanillaIdIgnoresBaseRomLookup() {
        // Even if a base ROM with the same id exists, a non-vanilla id must NOT use it.
        val resolve = resolverWith(
            baseRomLookup = { baseRom(it, File.createTempFile("base", ".z64").absolutePath) },
            storageRom = { File("/store/rom_$it") }
        )
        val result = resolve("ABCDEF01")
        assertEquals("/store/rom_ABCDEF01", result?.absolutePath)
    }

    @Test
    fun nonVanillaPrefixedIdResolvesViaStorageRom() {
        // Non-vanilla ids (e.g. store hacks, seeds) must resolve via storageRom, not the
        // base-ROM lookup (which only applies to vanilla_ ids).
        val resolve = resolverWith(
            baseRomLookup = { null },
            storageRom = { File("/store/rom_$it") }
        )
        val result = resolve("hack_seed123")
        assertEquals("/store/rom_hack_seed123", result?.absolutePath)
    }
}
