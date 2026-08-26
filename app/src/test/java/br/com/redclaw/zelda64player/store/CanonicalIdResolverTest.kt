package br.com.redclaw.zelda64player.store

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalIdResolverTest {

    private val ALIASES = """{"version":1,"aliases":{
        "hm_themissinglink":"the-missing-link",
        "hm_ocarinaoftime3d":"ocarina-of-time-3d",
        "hm_mm3d":"majoras-mask-3d"}}"""

    @Test
    fun normalizeStripsNamespaceAndNonAlnum() {
        assertEquals("themissinglink", CanonicalIdResolver.normalizeSlug("hm_themissinglink"))
        assertEquals("themissinglink", CanonicalIdResolver.normalizeSlug("the-missing-link"))
        assertEquals("themissinglink", CanonicalIdResolver.normalizeSlug("The_Missing.Link"))
        assertEquals("ocarinaoftimedx", CanonicalIdResolver.normalizeSlug("ocarina_of_time_dx"))
    }

    @Test
    fun aliasLookupResolvesHmToPicks() {
        CanonicalIdResolver.loadFromJson(ALIASES)
        assertEquals("themissinglink", CanonicalIdResolver.resolve("hm_themissinglink", "hylianmodding"))
        assertEquals("themissinglink", CanonicalIdResolver.resolve("the-missing-link", "picks"))
        assertEquals("ocarinaoftime3d", CanonicalIdResolver.resolve("hm_ocarinaoftime3d", "hylianmodding"))
    }

    @Test
    fun collisionSameCanonicalForDifferentStores() {
        CanonicalIdResolver.loadFromJson(ALIASES)
        val a = CanonicalIdResolver.resolve("hm_themissinglink", "hylianmodding")
        val b = CanonicalIdResolver.resolve("the-missing-link", "picks")
        assertEquals(a, b)
        assertEquals("themissinglink", a)
    }

    @Test
    fun missingAliasFallsBackToNormalization() {
        CanonicalIdResolver.loadFromJson(ALIASES)
        assertEquals("somehack", CanonicalIdResolver.resolve("some-hack", "picks"))
    }

    @Test
    fun emptyAliasMapNormalizesOnly() {
        CanonicalIdResolver.reset()
        assertEquals("themissinglink", CanonicalIdResolver.resolve("hm_themissinglink", "hylianmodding"))
    }
}
