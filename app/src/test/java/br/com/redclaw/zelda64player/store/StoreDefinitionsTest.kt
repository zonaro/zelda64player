package br.com.redclaw.zelda64player.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreDefinitionsTest {

    @Test
    fun sourcesAlwaysContainTheDefaultPicksCatalogOnlyOnce() {
        val sources = BuiltInStores.sources()

        assertEquals(1, sources.size)
        assertEquals(BuiltInStores.STORE_PICKS, sources.single().id)
        assertEquals(CatalogFetcher.DEFAULT_CATALOG_URL, sources.single().url)
    }

    @Test
    fun customCatalogsAreAdditionalPicksSources() {
        val sources = BuiltInStores.sources(listOf("https://example.com/catalog.json"))

        assertEquals(2, sources.size)
        assertEquals("picks-custom-0", sources[1].id)
        assertTrue(sources.none { it.url.contains("hylianmodding.com") })
    }
}
