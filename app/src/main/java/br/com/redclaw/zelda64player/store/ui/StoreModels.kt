package br.com.redclaw.zelda64player.store.ui

import br.com.redclaw.zelda64player.data.model.HackEntry

/** Install status of a catalog hack, derived for display in the store grid. */
sealed class StoreStatus {
    /** Not downloaded yet. */
    object NotInstalled : StoreStatus()

    /** Installed at the given version (matches the catalog). */
    data class Installed(val version: String) : StoreStatus()

    /** A newer version exists in the catalog than the installed one. */
    data class UpdateAvailable(val installedVersion: String, val catalogVersion: String) : StoreStatus()
}

/** A catalog hack paired with its computed display status. */
data class StoreItem(
    val hack: HackEntry,
    val status: StoreStatus
)
