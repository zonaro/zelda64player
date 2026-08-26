package br.com.redclaw.zelda64player.store.ui

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.store.CanonicalIdResolver

/**
 * Pure (Android-free) cross-catalog install-state logic, extracted from
 * [StoreViewModel] so it is unit-testable on the JVM.
 *
 * A hack is considered installed when ANY installed record shares its
 * [HackEntry.canonicalId] OR carries matching patch [br.com.redclaw.zelda64player.data.model.Checksums]
 * (CRC32 + MD5 + SHA-1). This makes the "Installed" badge appear in BOTH stores
 * when the hack was installed from either one (Phase 6 cross-catalog dedupe).
 */
object StoreStatusCalculator {

    fun statusFor(
        installed: Map<String, InstalledHacksRepository.InstalledHack>,
        hack: HackEntry
    ): StoreStatus {
        val canonical = hack.canonicalId
        val checksums = hack.patch?.checksums
        val match = installed.values.firstOrNull { inst ->
            inst.canonicalId == canonical ||
            (checksums != null && inst.patchChecksums != null &&
                inst.patchChecksums.crc32 == checksums.crc32 &&
                inst.patchChecksums.md5 == checksums.md5 &&
                inst.patchChecksums.sha1 == checksums.sha1)
        }
        return when {
            match == null -> StoreStatus.NotInstalled
            match.version != hack.version -> StoreStatus.UpdateAvailable(match.version, hack.version)
            else -> StoreStatus.Installed(match.version)
        }
    }

    /** True when any installed record shares [hackId]'s canonical id. */
    fun isInstalled(
        installed: Map<String, InstalledHacksRepository.InstalledHack>,
        hackId: String
    ): Boolean {
        val canonical = CanonicalIdResolver.resolve(hackId, "")
        return installed.values.any { it.canonicalId == canonical }
    }
}
