package br.com.redclaw.zelda64player.store

import android.content.Context
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.local.BaseRomRepository
import br.com.redclaw.zelda64player.data.local.RegisterResult
import br.com.redclaw.zelda64player.ocarina.OcarinaSongCatalog
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.patcher.n64.RomNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Registers a direct N64 file as a normalized vanilla base ROM, without patching it. */
class ImportedRomInstaller(
    private val context: Context,
    private val baseRomRepository: BaseRomRepository
) {
    suspend fun install(romFile: File, sourceName: String): ImportPatchResult =
        withContext(Dispatchers.IO) {
            val safeName = sourceName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val normalized = baseRomRepository.newImportTempFile(safeName)
            try {
                romFile.inputStream().use { input ->
                    normalized.outputStream().use { output -> RomNormalizer.normalize(input, output) }
                }
                when (val result = baseRomRepository.registerNormalizedFile(normalized, sourceName)) {
                    is RegisterResult.Success -> ImportRomSuccess(
                        result.rom.displayName,
                        OcarinaSongCatalog.detectGame(
                            RomHeader(result.rom.gameCode, result.rom.versionByte, "")
                        )
                    )
                    is RegisterResult.Duplicate -> ImportRomDuplicate(result.existing.displayName)
                    is RegisterResult.Invalid -> ImportRomInvalid(
                        context.getString(R.string.import_invalid_rom_message, result.reason)
                    )
                }
            } catch (error: Exception) {
                ImportRomInvalid(
                    context.getString(R.string.import_invalid_rom_message, error.message.orEmpty())
                )
            } finally {
                if (normalized.exists()) normalized.delete()
            }
        }
}
