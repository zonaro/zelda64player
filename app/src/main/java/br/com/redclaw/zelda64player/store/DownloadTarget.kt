package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.PatchRef

/**
 * Describes how a hack's patch bytes are obtained at download time. Built by the
 * catalog parsers from a mod's `download_link` (or, for PICKS, from the legacy
 * `patch` field). The Store detail dialog and the download pipeline branch on
 * this to decide between enqueueing a direct patch, resolving a GitHub release,
 * or opening the source in a browser.
 */
sealed class DownloadTarget {
    /** A directly-downloadable patch file (BPS/IPS/XDELTA/ZIP). */
    data class DirectPatch(val patch: PatchRef) : DownloadTarget()

    /** A GitHub releases page; the concrete asset URL is resolved at download time. */
    data class GitHubRelease(val repoUrl: String) : DownloadTarget()

    /** Any other link (HTML page, .7z/.rar/.ppf, or an unresolvable GitHub page):
     *  the user is sent to the source in a browser. */
    data class ExternalLink(val url: String) : DownloadTarget()
}
