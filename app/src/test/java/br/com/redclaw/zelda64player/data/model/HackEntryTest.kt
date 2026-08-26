package br.com.redclaw.zelda64player.data.model

import br.com.redclaw.zelda64player.store.CanonicalIdResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HackEntryTest {

    private val ALIASES = """{"version":1,"aliases":{
        "hm_themissinglink":"the-missing-link"}}"""

    private fun entry(
        id: String,
        storeId: String = "picks",
        checksums: Checksums? = null
    ) = HackEntry(
        id = id,
        name = id,
        description = "d",
        author = "a",
        version = "1.0",
        baseRom = BaseRomRef("b", "CZLJ", 0, Checksums("deadbeef")),
        patch = checksums?.let { PatchRef("", "$id.bps", 1, it) },
        storeId = storeId
    )

    @Test
    fun canonicalIdDelegatesToResolver() {
        CanonicalIdResolver.loadFromJson(ALIASES)
        assertEquals("themissinglink", entry("hm_themissinglink", "hylianmodding").canonicalId)
        assertEquals("themissinglink", entry("the-missing-link", "picks").canonicalId)
    }

    @Test
    fun isSameHackByCanonical() {
        CanonicalIdResolver.loadFromJson(ALIASES)
        val a = entry("hm_themissinglink", "hylianmodding")
        val b = entry("the-missing-link", "picks")
        assertTrue(HackEntry.isSameHack(a, b))
    }

    @Test
    fun isSameHackByChecksumsWhenCanonicalDiffers() {
        val c = Checksums("abc123", "md5val", "sha1val")
        val a = entry("hack_x", "picks", c)
        val b = entry("hack_y", "hylianmodding", c)
        assertTrue(HackEntry.isSameHack(a, b))
    }

    @Test
    fun isSameHackFalseWhenDifferent() {
        val a = entry("hack_x", "picks", Checksums("aaa", "m1", "s1"))
        val b = entry("hack_y", "picks", Checksums("bbb", "m2", "s2"))
        assertFalse(HackEntry.isSameHack(a, b))
    }

    @Test
    fun isSameHackFalseWhenOnlyOneHasChecksums() {
        val a = entry("hack_x", "picks", Checksums("aaa", "m1", "s1"))
        val b = entry("hack_y", "picks")
        assertFalse(HackEntry.isSameHack(a, b))
    }
}
