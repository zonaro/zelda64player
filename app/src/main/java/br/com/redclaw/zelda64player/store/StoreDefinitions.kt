package br.com.redclaw.zelda64player.store

/**
 * Multi-store catalog definitions. A [StoreDefinition] groups one or more
 * [CatalogSourceMeta] sources under a user-facing id/display name. The Store UI
 * filters the merged catalog by [StoreDefinition.id] so catalogs never mix.
 *
 * Two built-in stores ship with the app:
 *  - `hylianmodding` (Hylian Modding): 5 HYLIANMODDING sources (main index +
 *    four competition indexes). Default store.
 *  - `picks` (Zelda 64 Picks): a single PICKS source (the default catalog URL).
 *    User-added custom catalog URLs (Settings) are merged into this store for
 *    backward compatibility.
 */
enum class CatalogType { PICKS, HYLIANMODDING }

data class CatalogSourceMeta(
    val id: String,
    val type: CatalogType,
    val url: String,
    val displayName: String
)

data class StoreDefinition(
    val id: String,
    val displayName: String,
    val sources: List<CatalogSourceMeta>
)

/** Built-in store definitions and helpers for assembling the full source list. */
object BuiltInStores {
    const val STORE_HYLIANMODDING = "hylianmodding"
    const val STORE_PICKS = "picks"

    const val SOURCE_HM_MODS = "mods"
    private const val SOURCE_HM_CROSSOVER = "2025-crossover"
    private const val SOURCE_HM_HORROR = "2024-horror"
    private const val SOURCE_HM_ESCAPE = "2023-escape-room"
    private const val SOURCE_HM_JAM = "hm-jam-1"

    const val HM_BASE_URL = "https://hylianmodding.com"

    /** Display name for the PICKS store (overridable by catalog.json storeName). */
    const val PICKS_DEFAULT_NAME = "Zelda 64 Picks"

    /** Hylian Modding store: main index + four competition indexes. */
    val hylianModding: StoreDefinition = StoreDefinition(
        id = STORE_HYLIANMODDING,
        displayName = "Hylian Modding",
        sources = listOf(
            CatalogSourceMeta(
                id = SOURCE_HM_MODS,
                type = CatalogType.HYLIANMODDING,
                url = "$HM_BASE_URL/mods/index.json",
                displayName = "Hylian Modding"
            ),
            CatalogSourceMeta(
                id = SOURCE_HM_CROSSOVER,
                type = CatalogType.HYLIANMODDING,
                url = "$HM_BASE_URL/competitions/$SOURCE_HM_CROSSOVER/index.json",
                displayName = "2025 Crossover"
            ),
            CatalogSourceMeta(
                id = SOURCE_HM_HORROR,
                type = CatalogType.HYLIANMODDING,
                url = "$HM_BASE_URL/competitions/$SOURCE_HM_HORROR/index.json",
                displayName = "2024 Horror"
            ),
            CatalogSourceMeta(
                id = SOURCE_HM_ESCAPE,
                type = CatalogType.HYLIANMODDING,
                url = "$HM_BASE_URL/competitions/$SOURCE_HM_ESCAPE/index.json",
                displayName = "2023 Escape Room"
            ),
            CatalogSourceMeta(
                id = SOURCE_HM_JAM,
                type = CatalogType.HYLIANMODDING,
                url = "$HM_BASE_URL/competitions/$SOURCE_HM_JAM/index.json",
                displayName = "HM Jam 1"
            )
        )
    )

    /** PICKS store with a single source (the default catalog URL). */
    fun picksStore(customUrls: List<String> = emptyList()): StoreDefinition {
        val sources = buildList {
            add(
                CatalogSourceMeta(
                    id = STORE_PICKS,
                    type = CatalogType.PICKS,
                    url = CatalogFetcher.DEFAULT_CATALOG_URL,
                    displayName = PICKS_DEFAULT_NAME
                )
            )
            customUrls.forEachIndexed { index, url ->
                add(
                    CatalogSourceMeta(
                        id = "$STORE_PICKS-custom-$index",
                        type = CatalogType.PICKS,
                        url = url,
                        displayName = url
                    )
                )
            }
        }
        return StoreDefinition(
            id = STORE_PICKS,
            displayName = PICKS_DEFAULT_NAME,
            sources = sources
        )
    }

    /** All built-in stores, Hylian Modding first (default). */
    fun all(customUrls: List<String> = emptyList()): List<StoreDefinition> =
        listOf(hylianModding, picksStore(customUrls))
}
