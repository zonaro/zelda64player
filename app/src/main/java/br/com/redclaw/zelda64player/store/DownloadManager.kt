package br.com.redclaw.zelda64player.store

import android.content.Context
import android.util.Log
import br.com.redclaw.zelda64player.data.local.BaseRomRepository
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.PatchRepository
import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.patcher.PatcherException
import br.com.redclaw.zelda64player.patcher.PatcherFacade
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.api.RaUserAgent
import br.com.redclaw.zelda64player.retroachievements.data.RaHashService
import br.com.redclaw.zelda64player.retroachievements.data.RaInstallMetadataStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streaming download + apply of a hack's patch (BPS, possibly inside a `.zip`) with checksum
 * validation. On success the patched ROM is written to [Storage.rom] and the installed state is
 * recorded in [installedRepository]; the intermediate patch file is then discarded (the ROM is the
 * artifact, and a re-download is how the user updates). If patching fails (bad patch or missing
 * base ROM) the hack is NOT installed and any previous working ROM is left untouched: the new ROM
 * is built in a `.tmp` file and only renamed on success.
 *
 * The network layer is thin and the [OkHttpClient] is injected for testability; the
 * validation/extraction logic lives in [PatchValidator] and [ZipExtractor].
 */
class DownloadManager(
        private val context: Context,
        private val client: OkHttpClient,
        private val patchRepository: PatchRepository,
        private val installedRepository: InstalledHacksRepository,
        private val baseRomRepository: BaseRomRepository,
        private val storage: Storage
) {
        /**
         * Download and install [hack]. [onProgress] reports the current [InstallPhase] plus bytes
         * downloaded / total (for [InstallPhase.DOWNLOADING]; [InstallPhase.PATCHING] reports 0,0).
         * Returns the installed patched ROM file on success.
         */
        suspend fun download(
                hack: HackEntry,
                onProgress: (phase: InstallPhase, bytesDownloaded: Long, totalBytes: Long) -> Unit,
                cancelSignal: CancelSignal? = null
        ): Result<File> =
                withContext(Dispatchers.IO) {
                        Log.d(
                                "DownloadManager",
                                "download: start for ${hack.id}, patch=${hack.patch?.url}"
                        )
                        val patch =
                                hack.patch
                                        ?: return@withContext Result.failure(
                                                StoreException.GenericError(
                                                        "No patch available for this hack"
                                                )
                                        )
                        val tempArchive = File(patchRepository.directory, "${hack.id}.tmp")
                        val bpsTemp = File(patchRepository.directory, "${hack.id}.bps.tmp")
                        val romTemp = File(storage.storagePath, "rom_${hack.id}.tmp")
                        try {
                                val result = runCatching {
                                        // 1. Stream the patch archive to a temp file.
                                        Log.d(
                                                "DownloadManager",
                                                "download: connecting to ${patch.url}"
                                        )
                                        val response =
                                                client.newCall(
                                                                Request.Builder()
                                                                        .url(patch.url)
                                                                        .build()
                                                        )
                                                        .execute()
                                        try {
                                                if (!response.isSuccessful) {
                                                        throw StoreException.NetworkError(
                                                                "HTTP ${response.code}"
                                                        )
                                                }
                                                val total =
                                                        response.body?.contentLength()?.takeIf {
                                                                it > 0
                                                        }
                                                                ?: patch.size
                                                var downloaded = 0L
                                                response.body!!.byteStream().use { input ->
                                                        tempArchive.outputStream().use { output ->
                                                                val buf = ByteArray(64 * 1024)
                                                                var read: Int
                                                                while (input.read(buf).also {
                                                                        read = it
                                                                } != -1) {
                                                                        if (cancelSignal
                                                                                        ?.isCancelled ==
                                                                                        true
                                                                        ) {
                                                                                throw StoreException
                                                                                        .Cancelled()
                                                                        }
                                                                        output.write(buf, 0, read)
                                                                        downloaded += read
                                                                        onProgress(
                                                                                InstallPhase
                                                                                        .DOWNLOADING,
                                                                                downloaded,
                                                                                total
                                                                        )
                                                                }
                                                        }
                                                }

                                                // 2. Resolve the actual BPS bytes (extract from the
                                                // zip if needed).
                                                val bpsBytes =
                                                        if (patch.url.endsWith(
                                                                        ".zip",
                                                                        ignoreCase = true
                                                                )
                                                        ) {
                                                                val innerIsPatch =
                                                                        patch.filename.endsWith(
                                                                                ".bps",
                                                                                ignoreCase = true
                                                                        ) ||
                                                                                patch.filename
                                                                                        .endsWith(
                                                                                                ".ips",
                                                                                                ignoreCase =
                                                                                                        true
                                                                                        ) ||
                                                                                patch.filename
                                                                                        .endsWith(
                                                                                                ".xdelta",
                                                                                                ignoreCase =
                                                                                                        true
                                                                                        )
                                                                if (innerIsPatch) {
                                                                        ZipExtractor.extractEntry(
                                                                                tempArchive,
                                                                                patch.filename
                                                                        )
                                                                } else {
                                                                        // Archive declared without
                                                                        // an inner patch name: pick
                                                                        // the
                                                                        // first patch-like entry
                                                                        // inside it.
                                                                        ZipExtractor
                                                                                .extractFirstMatching(
                                                                                        tempArchive,
                                                                                        ".*\\.(bps|ips|xdelta)$"
                                                                                )
                                                                }
                                                        } else {
                                                                tempArchive.readBytes()
                                                        }

                                                // 3. Validate against the catalog-declared
                                                // checksums.
                                                PatchValidator.validate(bpsBytes, patch.checksums)
                                                        .onFailure { throw it }
                                                bpsTemp.writeBytes(bpsBytes)

                                                // 4. Resolve the base ROM (BPS needs a matching
                                                // one; IPS is
                                                // self-contained).
                                                baseRomRepository.scanAndRegister()
                                                val baseFile = resolveBaseFile(bpsTemp, hack)

                                                // 5. Apply the patch to a temp ROM on the SAME
                                                // filesystem as the final
                                                // target.
                                                if (cancelSignal?.isCancelled == true) {
                                                        throw StoreException.Cancelled()
                                                }
                                                onProgress(InstallPhase.PATCHING, 0, 0)
                                                PatcherFacade.applyPatchBlocking(
                                                                baseFile,
                                                                bpsTemp,
                                                                romTemp
                                                        )
                                                        .getOrElse { throw mapPatcherException(it) }

                                                // 6. Atomically publish: rename temp ROM -> final,
                                                // record install, drop
                                                // patch.
                                                val finalRom = storage.rom(hack.id)
                                                if (!romTemp.renameTo(finalRom)) {
                                                        romTemp.inputStream().use { input ->
                                                                finalRom.outputStream().use { output
                                                                        ->
                                                                        input.copyTo(output)
                                                                }
                                                        }
                                                        romTemp.delete()
                                                }
                                                val patchChecksums =
                                                        CanonicalIdResolver.computePatchChecksums(
                                                                bpsBytes
                                                        )
                                                installedRepository.markInstalled(
                                                        hack.id,
                                                        hack.version,
                                                        patch.filename,
                                                        hack.canonicalId,
                                                        patchChecksums
                                                )
                                                patchRepository.delete(hack.id)

                                                // 7. RetroAchievements identity: hash the FINAL
                                                // patched ROM
                                                //    (never the base ROM or an intermediate) and
                                                // resolve its
                                                //    game id. Best-effort: failures leave a
                                                // placeholder entry
                                                //    that is retried on a later install/launch. A
                                                // catalog
                                                //    provided retroAchievements.gameId seeds the
                                                // identity so
                                                //    the library screens work even before/offline
                                                // resolution.
                                                runCatching {
                                                        val metadataStore =
                                                                RaInstallMetadataStore(context)
                                                        hack.retroAchievements
                                                                ?.takeIf { it.gameId != 0L }
                                                                ?.let { ref ->
                                                                        metadataStore.put(
                                                                                hack.id,
                                                                                br.com.redclaw
                                                                                        .zelda64player
                                                                                        .retroachievements
                                                                                        .data
                                                                                        .RaGameIdentity(
                                                                                                raHash =
                                                                                                        "",
                                                                                                gameId =
                                                                                                        ref.gameId,
                                                                                                title =
                                                                                                        ref.title
                                                                                        )
                                                                        )
                                                                }
                                                        RaHashService(
                                                                        http =
                                                                                RaHttpClient(
                                                                                        RaUserAgent
                                                                                                .build(
                                                                                                        context
                                                                                                )
                                                                                ),
                                                                        metadataStore =
                                                                                metadataStore
                                                                )
                                                                .computeAndResolve(
                                                                        hack.id,
                                                                        finalRom
                                                                )
                                                }

                                                finalRom
                                        } finally {
                                                response.close()
                                        }
                                }
                                result.onFailure { romTemp.delete() }
                                result
                        } finally {
                                tempArchive.delete()
                                bpsTemp.delete()
                        }
                }

        /**
         * Pick the base ROM file to apply [patchFile] against. For IPS patches a zero-length
         * placeholder is returned (the applier ignores it). For BPS the patch's expected source
         * CRC32 is matched against imported base ROMs. For xdelta3 (VCDIFF) the patch carries no
         * source CRC, so the catalog-declared base ROM CRC drives resolution — falling back to the
         * game code for stores (e.g. Hylian Modding) that only declare the target game. If no
         * imported base ROM matches, [StoreException.BaseRomMissing] is thrown so the hack is not
         * installed.
         */
        private fun resolveBaseFile(patchFile: File, hack: HackEntry): File {
                val format = PatcherFacade.detectPatchFormat(patchFile)
                if (format == PatcherFacade.PatchFormat.IPS) {
                        return emptyBaseFile()
                }
                val roms = baseRomRepository.getAll()
                val baseRom =
                        if (format == PatcherFacade.PatchFormat.XDELTA) {
                                // xdelta3 carries no source CRC, so resolution is driven by the
                                // catalog-declared base ROM CRC — falling back to the game code for
                                // stores (e.g. Hylian Modding) that only declare the target game.
                                findBaseRomForHack(roms, hack)
                                        ?: throw StoreException.BaseRomMissing(
                                                hack.baseRom.checksums.crc32.ifBlank {
                                                        hack.baseRom.gameCode
                                                },
                                                roms.map { it.crc32 }
                                        )
                        } else {
                                // BPS: read the authoritative source CRC from the patch itself.
                                val expectedCrc =
                                        PatcherFacade.expectedSourceCrc32(patchFile).getOrElse {
                                                throw StoreException.InvalidPatch(
                                                        "Cannot read BPS source CRC32: ${it.message}"
                                                )
                                        }
                                findBaseRomByCrc(roms, expectedCrc)
                                        ?: throw StoreException.BaseRomMissing(
                                                expectedCrc,
                                                roms.map { it.crc32 }
                                        )
                        }
                return File(baseRom.path)
        }

        /** A zero-length placeholder base ROM for IPS patches (ignored by the applier). */
        private fun emptyBaseFile(): File =
                File(patchRepository.directory, "ips_empty_base.bin").also {
                        if (!it.exists()) it.writeBytes(ByteArray(0))
                }

        /** Map a patcher failure to a user-facing [StoreException]. */
        private fun mapPatcherException(e: Throwable): StoreException =
                when (e) {
                        is StoreException -> e
                        is PatcherException.PatchFormatError ->
                                StoreException.InvalidPatch(
                                        e.message ?: "Unrecognized patch format"
                                )
                        is PatcherException.SourceChecksumMismatch ->
                                StoreException.InvalidPatch(
                                        "Base ROM checksum mismatch: ${e.message}"
                                )
                        is PatcherException.TargetChecksumMismatch ->
                                StoreException.InvalidPatch(
                                        "Patched ROM checksum mismatch: ${e.message}"
                                )
                        is PatcherException.PatchChecksumMismatch ->
                                StoreException.InvalidPatch("Patch checksum mismatch: ${e.message}")
                        is PatcherException.RomFormatError ->
                                StoreException.InvalidPatch(e.message ?: "Invalid ROM format")
                        else -> StoreException.GenericError(e.message ?: "Patch application failed")
                }
}

/**
 * Pure base-ROM resolution used by [DownloadManager.resolveBaseFile]: returns the first imported
 * ROM whose CRC32 matches [expectedCrc] (case-insensitive), or null. Extracted (no Android deps) so
 * it can be unit-tested directly.
 */
internal fun findBaseRomByCrc(roms: List<BaseRom>, expectedCrc: String): BaseRom? =
        roms.firstOrNull { it.crc32.equals(expectedCrc, ignoreCase = true) }

/**
 * First imported ROM whose [BaseRom.gameCode] matches [code] (case-insensitive), or null. Used as a
 * fallback when a catalog entry declares only the target game (e.g. Hylian Modding) and leaves the
 * base ROM CRC empty.
 */
internal fun findBaseRomByGameCode(roms: List<BaseRom>, code: String): BaseRom? =
        roms.firstOrNull { it.gameCode.equals(code, ignoreCase = true) }

/**
 * Resolve the imported base ROM required by [hack].
 *
 * Catalogs that declare a base ROM CRC (e.g. PICKS) match by CRC32. Hylian Modding entries only
 * declare the target game (OoT/MM) via [BaseRomRef.gameCode] and leave the CRC empty, so when no
 * CRC is available we fall back to matching by game code — the user "has the ROM" as long as they
 * imported the right game.
 */
internal fun findBaseRomForHack(roms: List<BaseRom>, hack: HackEntry): BaseRom? {
        val crc = hack.baseRom.checksums.crc32
        if (crc.isNotBlank()) return findBaseRomByCrc(roms, crc)
        val code = hack.baseRom.gameCode
        if (code.isBlank()) return null
        return findBaseRomByGameCode(roms, code)
}
