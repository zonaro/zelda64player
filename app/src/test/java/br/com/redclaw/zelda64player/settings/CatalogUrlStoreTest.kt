package br.com.redclaw.zelda64player.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogUrlStoreTest {

    private class InMemoryStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String, default: String?): String? = map[key] ?: default
        override fun putString(key: String, value: String) {
            map[key] = value
        }
    }

    private fun newStore() = CatalogUrlStore(InMemoryStore(), "catalog_urls")

    @Test
    fun emptyByDefault() {
        assertTrue(newStore().getUrls().isEmpty())
    }

    @Test
    fun addAndReadRoundTrip() {
        val store = newStore()
        assertTrue(store.addUrl("https://example.com/catalog.json"))
        assertTrue(store.addUrl("https://other.com/c.json"))
        assertEquals(
            listOf("https://example.com/catalog.json", "https://other.com/c.json"),
            store.getUrls()
        )
    }

    @Test
    fun persistenceRoundTripThroughRawString() {
        val backing = InMemoryStore()
        val storeA = CatalogUrlStore(backing, "catalog_urls")
        storeA.addUrl("https://example.com/catalog.json")
        // A fresh store over the same backing must reload the persisted JSON.
        val storeB = CatalogUrlStore(backing, "catalog_urls")
        assertEquals(listOf("https://example.com/catalog.json"), storeB.getUrls())
    }

    @Test
    fun rejectsInvalidSchemes() {
        val store = newStore()
        assertFalse(store.addUrl("ftp://example.com/c.json"))
        assertFalse(store.addUrl("not a url"))
        assertFalse(store.addUrl("https://"))
        assertFalse(store.addUrl(""))
        assertTrue(store.getUrls().isEmpty())
    }

    @Test
    fun deduplicatesCaseInsensitively() {
        val store = newStore()
        assertTrue(store.addUrl("https://example.com/c.json"))
        assertFalse(store.addUrl("https://EXAMPLE.com/c.json"))
        assertEquals(1, store.getUrls().size)
    }

    @Test
    fun removeUrlWorks() {
        val store = newStore()
        store.addUrl("https://example.com/c.json")
        assertTrue(store.removeUrl("https://example.com/c.json"))
        assertFalse(store.removeUrl("https://example.com/c.json"))
        assertTrue(store.getUrls().isEmpty())
    }

    @Test
    fun isValidUrlHelper() {
        assertTrue(CatalogUrlStore.isValidUrl("http://a.b/c"))
        assertTrue(CatalogUrlStore.isValidUrl("https://a.b/c"))
        assertFalse(CatalogUrlStore.isValidUrl("file:///etc/passwd"))
        assertFalse(CatalogUrlStore.isValidUrl("javascript:alert(1)"))
    }
}
