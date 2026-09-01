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
import br.com.redclaw.zelda64player.ocarina.OcarinaSongCatalog
import br.com.redclaw.zelda64player.patcher.PatcherException
import br.com.redclaw.zelda64player.patcher.PatcherFacade
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.views.LocalPatchesSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Installs a user-imported BPS/IPS patch as a playable Library entry.
 *
 * Mirrors the proven logic in [br.com.redclaw.zelda64player.store.DownloadManager]: for BPS it
 * reads the patch's expected source CRC32 and matches it against the user's imported base ROMs
 * (reusing [findBaseRomByCrc]); for IPS the patch is self-contained. On success the patched ROM is
 * written to [Storage.rom] and the hack is recorded in [UserHacksRepository] +
 * [InstalledHacksRepository] so it surfaces in the Library. On a missing base ROM it reports which
 * game the patch targets (via [KnownBaseRomTable]) instead of failing silently.
 */
class ImportedPatchInstaller(
        private val context: Context,
        private val baseRomRepository: BaseRomRepository,
        private val installedRepository: InstalledHacksRepository,
        private val userHacksRepository: UserHacksRepository,
        private val storage: Storage
) {
        suspend fun install(patchFile: File, suggestedName: String): ImportPatchResult =
                withContext(Dispatchers.IO) {
                        val format = PatcherFacade.detectPatchFormat(patchFile)
                        if (format == PatcherFacade.PatchFormat.UNKNOWN) {
                                return@withContext ImportPatchUnsupported(
                                        safeString(
                                                R.string.import_unsupported_message,
                                                "Unsupported patch format"
                                        )
                                )
                        }

                        val baseRom: BaseRom? =
                                if (format == PatcherFacade.PatchFormat.BPS) {
                                        val expectedCrc =
                                                PatcherFacade.expectedSourceCrc32(patchFile)
                                                        .getOrElse {
                                                                return@withContext ImportPatchInvalid(
                                                                        safeString(
                                                                                R.string
                                                                                        .import_invalid_message,
                                                                                "Invalid patch: ${it.message ?: ""}",
                                                                                it.message ?: ""
                                                                        )
                                                                )
                                                        }
                                        val found =
                                                findBaseRomByCrc(
                                                        baseRomRepository.getAll(),
                                                        expectedCrc
                                                )
                                        if (found == null) {
                                                val info = KnownBaseRomTable.infoFor(expectedCrc)
                                                val description =
                                                        info?.let {
                                                                safeString(
                                                                        gameNameRes(it.game),
                                                                        it.game?.name ?: "Unknown"
                                                                ) + " (" + it.versionLabel + ")"
                                                        }
                                                return@withContext ImportPatchNoCompatibleRom(
                                                        expectedCrc32 = expectedCrc,
                                                        targetDescription = description,
                                                        foundCrc32s =
                                                                baseRomRepository.getAll().map { rom
                                                                        ->
                                                                        rom.crc32
                                                                }
                                                )
                                        }
                                        found
                                } else {
                                        null
                                }

                        val hackId = uniqueHackId(suggestedName)
                        val romTemp = File(storage.storagePath, "rom_${hackId}.tmp")
                        try {
                                val baseFile = baseRom?.let { File(it.path) } ?: emptyBaseFile()
                                PatcherFacade.applyPatchBlocking(baseFile, patchFile, romTemp)
                                        .getOrElse { e ->
                                                return@withContext ImportPatchInvalid(mapMessage(e))
                                        }

                                val finalRom = storage.rom(hackId)
                                if (!romTemp.renameTo(finalRom)) {
                                        romTemp.inputStream().use { input ->
                                                finalRom.outputStream().use { output ->
                                                        input.copyTo(output)
                                                }
                                        }
                                        romTemp.delete()
                                }

                                val entry =
                                        HackEntry(
                                                id = hackId,
                                                name = LocalPatchesSource.prettify(suggestedName),
                                                description =
                                                        safeString(
                                                                R.string.import_description_auto,
                                                                "Imported patch"
                                                        ),
                                                author =
                                                        safeString(
                                                                R.string.import_author_user,
                                                                "User"
                                                        ),
                                                version = "1.0",
                                                baseRom =
                                                        baseRom?.let {
                                                                BaseRomRef(
                                                                        name = it.displayName,
                                                                        gameCode = it.gameCode,
                                                                        versionByte =
                                                                                it.versionByte,
                                                                        checksums =
                                                                                Checksums(
                                                                                        it.crc32,
                                                                                        it.md5,
                                                                                        it.sha1
                                                                                )
                                                                )
                                                        }
                                                                ?: BaseRomRef(
                                                                        name =
                                                                                safeString(
                                                                                        R.string
                                                                                                .import_unknown_base,
                                                                                        "Unknown"
                                                                                ),
                                                                        gameCode = "",
                                                                        versionByte = 0,
                                                                        checksums =
                                                                                Checksums(
                                                                                        "",
                                                                                        null,
                                                                                        null
                                                                                )
                                                                ),
                                                patch =
                                                        PatchRef(
                                                                url = "",
                                                                filename = patchFile.name,
                                                                size = patchFile.length(),
                                                                checksums =
                                                                        Checksums(
                                                                                ChecksumCalculator
                                                                                        .crc32(
                                                                                                patchFile
                                                                                        ),
                                                                                null,
                                                                                null
                                                                        )
                                                        ),
                                                coverImageUrl = null
                                        )
                                userHacksRepository.add(entry)
                                val patchChecksums =
                                        CanonicalIdResolver.computePatchChecksums(
                                                patchFile.readBytes()
                                        )
                                installedRepository.markInstalled(
                                        hackId,
                                        "1.0",
                                        patchFile.name,
                                        entry.canonicalId,
                                        patchChecksums
                                )

                                val family =
                                        baseRom?.let {
                                                OcarinaSongCatalog.detectGame(
                                                        RomHeader(it.gameCode, it.versionByte, "")
                                                )
                                        }
                                ImportPatchSuccess(hackId, entry.name, family)
                        } finally {
                                if (romTemp.exists()) romTemp.delete()
                        }
                }

        private fun gameNameRes(game: br.com.redclaw.zelda64player.ocarina.OcarinaGame?): Int =
                when (game) {
                        br.com.redclaw.zelda64player.ocarina.OcarinaGame.OOT -> R.string.game_oot
                        br.com.redclaw.zelda64player.ocarina.OcarinaGame.MM -> R.string.game_mm
                        null -> R.string.game_unknown
                }

        private fun mapMessage(e: Throwable): String {
                val detail =
                        when (e) {
                                is PatcherException.PatchFormatError -> e.message
                                                ?: "Unrecognized patch format"
                                is PatcherException.SourceChecksumMismatch ->
                                        "Base ROM checksum mismatch: ${e.message}"
                                is PatcherException.TargetChecksumMismatch ->
                                        "Patched ROM checksum mismatch: ${e.message}"
                                is PatcherException.PatchChecksumMismatch ->
                                        "Patch checksum mismatch: ${e.message}"
                                is PatcherException.RomFormatError -> e.message
                                                ?: "Invalid ROM format"
                                else -> e.message ?: "Patch application failed"
                        }
                return safeString(R.string.import_invalid_message, "Invalid patch: $detail", detail)
        }

        private fun safeString(resId: Int, fallback: String, vararg args: Any): String {
                return try {
                        if (args.isEmpty()) context.getString(resId)
                        else context.getString(resId, *args)
                } catch (_: Throwable) {
                        if (args.isEmpty()) fallback else String.format(fallback, *args)
                }
        }

        /** A zero-length placeholder base ROM for IPS patches (ignored by the applier). */
        private fun emptyBaseFile(): File =
                File(context.cacheDir, "ips_empty_base.bin").also {
                        if (!it.exists()) it.writeBytes(ByteArray(0))
                }

        /**
         * Derive a stable, filesystem-safe hack id from [suggestedName] (the patch filename without
         * extension). Ensures uniqueness against existing user hacks by appending a numeric suffix
         * on collision.
         */
        private fun uniqueHackId(suggestedName: String): String {
                val base =
                        suggestedName
                                .substringBeforeLast('.')
                                .lowercase()
                                .replace(Regex("[^a-z0-9]+"), "_")
                                .trim('_')
                                .ifBlank { "imported_hack" }
                val existing = userHacksRepository.getAll().map { it.id }.toSet()
                var candidate = base
                var suffix = 2
                while (existing.contains(candidate)) {
                        candidate = "${base}_${suffix++}"
                }
                return candidate
        }
}
