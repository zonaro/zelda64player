package br.com.redclaw.zelda64player.store

/** One catalog source contributing entries to Main Store. */
data class CatalogSourceMeta(val id: String, val url: String, val displayName: String)

/** Main Store catalog sources. Custom URLs remain supported as Main Store sources. */
object BuiltInStores {
    const val STORE_PICKS = "picks"
    const val PICKS_DEFAULT_NAME = "Main Store"

    /** Default catalog plus user-added Picks catalogs. */
    fun sources(customUrls: List<String> = emptyList()): List<CatalogSourceMeta> = buildList {
        add(
                CatalogSourceMeta(
                        id = STORE_PICKS,
                        url = CatalogFetcher.DEFAULT_CATALOG_URL,
                        displayName = PICKS_DEFAULT_NAME
                )
        )
        customUrls.forEachIndexed { index, url ->
            add(CatalogSourceMeta(id = "$STORE_PICKS-custom-$index", url = url, displayName = url))
        }
    }
}
