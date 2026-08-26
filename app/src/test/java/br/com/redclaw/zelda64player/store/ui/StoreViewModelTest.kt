package br.com.redclaw.zelda64player.store.ui

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.data.model.PatchRef
import br.com.redclaw.zelda64player.store.CanonicalIdResolver
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the cross-catalog install-state logic used by [StoreViewModel.statusFor]
 * (delegated to [StoreStatusCalculator]) and [StoreViewModel.isInstalled].
 */
class StoreViewModelTest {

    private fun hack(id: String, storeId: String, version: String, crc: String = "crc"): HackEntry =
        HackEntry(
            id = id, name = id, description = "", author = "x", version = version,
            baseRom = BaseRomRef("OoT", "CZLE", 0, Checksums("x")),
            storeId = storeId,
            patch = PatchRef(url = "", filename = "", size = 0, checksums = Checksums(crc))
        )

    private fun installed(
        hackId: String,
        version: String,
        canonicalId: String,
        crc: String = "crc"
    ): InstalledHacksRepository.InstalledHack = InstalledHacksRepository.InstalledHack(
        hackId = hackId,
        version = version,
        fileName = "rom_$hackId",
        canonicalId = canonicalId,
        patchChecksums = Checksums(crc)
    )

    @Test
    fun statusForInstalledWhenOtherStoreVariantInstalled() {
        CanonicalIdResolver.reset()
        val installed = mapOf(
            "hm_themissinglink" to installed("hm_themissinglink", "1.0", "themissinglink", "crc")
        )
        // Querying the PICKS variant (different id, same canonical id).
        val picks = hack("the-missing-link", "picks", "1.0", "crc")
        assertEquals(
            StoreStatus.Installed("1.0"),
            StoreStatusCalculator.statusFor(installed, picks)
        )
    }

    @Test
    fun statusForNotInstalledWhenNoMatch() {
        CanonicalIdResolver.reset()
        val installed = mapOf(
            "hm_other" to installed("hm_other", "1.0", "other", "crc")
        )
        val picks = hack("the-missing-link", "picks", "1.0", "different")
        assertEquals(
            StoreStatus.NotInstalled,
            StoreStatusCalculator.statusFor(installed, picks)
        )
    }

    @Test
    fun isInstalledMatchesAcrossStores() {
        CanonicalIdResolver.reset()
        val installed = mapOf(
            "hm_themissinglink" to installed("hm_themissinglink", "1.0", "themissinglink")
        )
        assertEquals(true, StoreStatusCalculator.isInstalled(installed, "the-missing-link"))
        assertEquals(false, StoreStatusCalculator.isInstalled(installed, "unrelated"))
    }
}
