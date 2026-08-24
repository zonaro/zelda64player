package br.com.redclaw.zelda64player.store

import android.content.Context
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.local.BaseRomRepository
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.UserHacksRepository
import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.data.model.PatchRef
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.ocarina.OcarinaSongCatalog
import br.com.redclaw.zelda64player.patcher.PatcherException
import br.com.redclaw.zelda64player.patcher.PatcherFacade
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.views.LocalPatchesSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs a user-imported patch (BPS or IPS) into the Library.
 *
 * The flow mirrors [br.com.redclaw.zelda64player.store.DownloadManager] but
 * without any network step: the patch bytes already live on disk. It reuses the
 * same building blocks — [PatcherFacade] for format detection/apply,
 * [findBaseRomByCrc] for BPS base-ROM matching, and [Storage.rom] for the final
 * patched ROM — so an imported hack is indistinguishable from a catalog hack at
 * launch time (Rule 10: the only place ROM bytes reach the core is
 * [br.com.redclaw.zelda64player.retroview.RetroView]).
 *
 * IPS patches are self-contained (no base ROM needed); a zero-length placeholder
 * base file is used, replicating [DownloadManager]'s `emptyBaseFile()` helper.
 */
class ImportedPatchInstaller(
    private val context: Context,
    private val baseRomRepository: BaseRomRepository,
    private val installedRepository: InstalledHacksRepository,
    private val userHacksRepository: UserHacksRepository,
    private val storage: Storage,
    private val stringResolver: (Int) -> String = { context.getString(it) }
) {
    suspend fun install(patchFile: File, suggestedName: String): ImportPatchResult =
        withContext<ImportPatchResult>(Dispatchers.IO) {
            val format = PatcherFacade.detectPatchFormat(patchFile)
            if (format == PatcherFacade.PatchFormat.UNKNOWN) {
                return@withContext ImportPatchUnsupported(
                    stringResolver(R.string.import_unsupported_message)
                )
            }

            // Resolve the base ROM. BPS needs a matching imported base ROM; IPS
            // is self-contained (null base).
            val baseRom: BaseRom? = if (format == PatcherFacade.PatchFormat.BPS) {
                val expectedCrc = PatcherFacade.expectedSourceCrc32(patchFile)
                    .getOrElse { e -> return@withContext ImportPatchInvalid(mapMessage(e)) }
                val found = findBaseRomByCrc(baseRomRepository.getAll(), expectedCrc)
                if (found == null) {
                    val info = KnownBaseRomTable.infoFor(expectedCrc)
                    val targetDescription = info?.let {
                        stringResolver(gameNameRes(it.game)) + " (" + it.versionLabel + ")"
                    }
                    return@withContext ImportPatchNoCompatibleRom(
                        expectedCrc32 = expectedCrc,
                        targetDescription = targetDescription,
                        foundCrc32s = baseRomRepository.getAll().map { it.crc32 }
                    )
                }
                found
            } else {
                null
            }

            val hackId = uniqueHackId(slugify(suggestedName))
            val romTemp = File(storage.storagePath, "rom_${hackId}.tmp")
            try {
                val baseFile = baseRom?.let { File(it.path) } ?: emptyBaseFile()
                PatcherFacade.applyPatchBlocking(baseFile, patchFile, romTemp)
                    .getOrElse { e -> return@withContext ImportPatchInvalid(mapMessage(e)) }

                // Atomically publish: rename temp ROM -> final, record install.
                val finalRom = storage.rom(hackId)
                if (!romTemp.renameTo(finalRom)) {
                    romTemp.inputStream().use { input ->
                        finalRom.outputStream().use { output -> input.copyTo(output) }
                    }
                    romTemp.delete()
                }

                val entry = HackEntry(
                    id = hackId,
                    name = LocalPatchesSource.prettify(suggestedName),
                    description = stringResolver(R.string.import_description_auto),
                    author = stringResolver(R.string.import_author_user),
                    version = "1.0",
                    baseRom = baseRom?.let {
                        BaseRomRef(
                            it.displayName,
                            it.gameCode,
                            it.versionByte,
                            Checksums(it.crc32, it.md5, it.sha1)
                        )
                    } ?: BaseRomRef(
                        stringResolver(R.string.import_unknown_base),
                        "",
                        0,
                        Checksums("", null, null)
                    ),
                    patch = PatchRef(
                        url = "",
                        filename = patchFile.name,
                        size = patchFile.length(),
                        checksums = Checksums(ChecksumCalculator.crc32(patchFile), null, null)
                    ),
                    coverImageUrl = null
                )
                userHacksRepository.add(entry)
                installedRepository.markInstalled(hackId, "1.0", patchFile.name)

                val family = baseRom?.let {
                    OcarinaSongCatalog.detectGame(RomHeader(it.gameCode, it.versionByte, ""))
                }
                ImportPatchSuccess(hackId, entry.name, family)
            } finally {
                romTemp.delete()
            }
        }

    /** Map a patcher failure to a user-facing message string. */
    private fun mapMessage(e: Throwable): String = when (e) {
        is PatcherException -> e.message ?: stringResolver(R.string.import_invalid_generic)
        else -> stringResolver(R.string.import_invalid_generic)
    }

    /** Resource id for a game family's name (used to build target descriptions). */
    private fun gameNameRes(game: OcarinaGame?): Int = when (game) {
        OcarinaGame.OOT -> R.string.game_oot
        OcarinaGame.MM -> R.string.game_mm
        null -> R.string.game_unknown
    }

    /**
     * A zero-length placeholder base ROM for IPS patches (ignored by the
     * applier). Mirrors [DownloadManager.emptyBaseFile] locally so this installer
     * has no dependency on that class.
     */
    private fun emptyBaseFile(): File =
        File(context.cacheDir, "ips_empty_base.bin").also {
            if (!it.exists()) it.writeBytes(ByteArray(0))
        }

    /**
     * Turn a suggested name (patch filename without extension) into a stable,
     * filesystem/url-safe id: lowercase, non `[a-z0-9]` collapsed to single
     * underscores, trimmed of leading/trailing underscores.
     */
    private fun slugify(name: String): String {
        val cleaned = name.lowercase()
            .replace(NON_SLUG, "_")
            .replace(REPEAT_UNDERSCORE, "_")
            .trim('_')
        return if (cleaned.isBlank()) "imported_hack" else cleaned
    }

    /** Ensure [base] is unique among existing user hacks by appending _2, _3, … */
    private fun uniqueHackId(base: String): String {
        if (userHacksRepository.getById(base) == null) return base
        var n = 2
        while (userHacksRepository.getById("${base}_$n") != null) n++
        return "${base}_$n"
    }

    private companion object {
        val NON_SLUG = Regex("[^a-z0-9]+")
        val REPEAT_UNDERSCORE = Regex("_+")
    }
}
