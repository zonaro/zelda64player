package br.com.redclaw.zelda64player.randomizer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.patcher.n64.RomNormalizer
import br.com.redclaw.zelda64player.randomizer.BaseRomValidator
import br.com.redclaw.zelda64player.randomizer.api.OotrApiClient
import br.com.redclaw.zelda64player.randomizer.api.OotrApiException
import br.com.redclaw.zelda64player.randomizer.api.OotrApiKeyStore
import br.com.redclaw.zelda64player.randomizer.api.SeedCreateResponse
import br.com.redclaw.zelda64player.randomizer.api.SeedPoller
import br.com.redclaw.zelda64player.randomizer.api.SeedStatus
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchException
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatcherFacade
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedEntry
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedRepository
import br.com.redclaw.zelda64player.randomizer.settings.PlandomizerValidator
import br.com.redclaw.zelda64player.randomizer.settings.RandomizerSettingsSchema
import br.com.redclaw.zelda64player.randomizer.settings.SchemaLoader
import br.com.redclaw.zelda64player.randomizer.settings.SettingsStateBuilder
import br.com.redclaw.zelda64player.randomizer.settings.SettingsValidator
import br.com.redclaw.zelda64player.settings.FileNameSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Drives the Randomizer screen: loads the settings schema, holds the form
 * state, and runs the full generation pipeline
 * (key check -> validate -> build settings -> create seed -> poll -> download
 * patch -> resolve base ROM -> apply patch).
 *
 * All network and file I/O run on [Dispatchers.IO]; the pipeline is cancellable
 * through [viewModelScope] (the polling loop checks cancellation on every
 * iteration).
 */
class RandomizerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val schemaLoader = Zelda64PlayerApp.randomizerSchemaLoader
    private val apiKeyStore = Zelda64PlayerApp.ootrApiKeyStore
    private val apiClient = Zelda64PlayerApp.ootrApiClient
    private val baseRomRepository = AppRepositories.baseRomRepository(appContext)
    private val seedRepository = AppRepositories.randomizedSeedRepository(appContext)

    private val _schema = MutableStateFlow<RandomizerSettingsSchema?>(null)
    val schema: StateFlow<RandomizerSettingsSchema?> = _schema

    private val _generation = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generation: StateFlow<GenerationState> = _generation

    /** User-facing name for the resulting seed (required, <= ~60 chars). */
    val seedName = MutableStateFlow("")

    /** Optional fixed seed string for reproducible generation. */
    val seedString = MutableStateFlow<String?>(null)

    /** Selected OoTR version pin (null = server default). */
    val selectedVersion = MutableStateFlow<String?>(null)

    /** Available versions fetched from the API (empty on failure). */
    val availableVersions = MutableStateFlow<List<String>>(emptyList())

    /**
     * Raw Plandomizer placement JSON text edited by the user (single source of
     * truth for the editor). `null` or blank means "no plandomizer".
     */
    val plandomizerText = MutableStateFlow<String?>(null)

    /** Single source of truth for form values, keyed by option name. */
    val formValues: MutableMap<String, Any?> = mutableMapOf()

    private var generateJob: Job? = null

    /**
     * Set to `true` when a Plandomizer placement was attached but the API
     * rejected it (HTTP 400) and generation succeeded only after retrying
     * without it. Surfaced as [GenerationState.SuccessWithoutPlandomizer].
     */
    private var plandomizerRejected = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { schemaLoader.load() }.onSuccess { schema ->
                val defaults = mutableMapOf<String, Any?>()
                schema.categories.forEach { cat ->
                    cat.options.forEach { opt -> defaults[opt.name] = opt.default }
                }
                formValues.putAll(defaults)
                _schema.value = schema
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { apiClient.fetchAvailableVersions("master") }
                .onSuccess { versions ->
                    availableVersions.value = versions
                    if (selectedVersion.value == null && versions.isNotEmpty()) {
                        selectedVersion.value = versions.first()
                    }
                }
        }
    }

    /** Update a single form value (called by the settings renderer). */
    fun setValue(name: String, value: Any?) {
        formValues[name] = value
    }

    /** Begin (or restart) the generation pipeline. Ignored while running. */
    fun generate() {
        if (generateJob?.isActive == true) return
        generateJob = viewModelScope.launch(Dispatchers.IO) { runGeneration() }
    }

    /** Cancel an in-flight generation and return to idle. */
    fun cancelGeneration() {
        generateJob?.cancel()
        _generation.value = GenerationState.Idle
    }

    private suspend fun runGeneration() {
        val schema = _schema.value ?: run {
            _generation.value = GenerationState.Error(GenerationError.Unknown("schema not loaded"))
            return
        }
        val apiKey = apiKeyStore.getKey()
        if (apiKey.isNullOrBlank()) {
            _generation.value = GenerationState.Error(GenerationError.MissingApiKey)
            return
        }
        val offending = SettingsValidator.validate(schema, formValues)
        if (offending.isNotEmpty()) {
            _generation.value = GenerationState.Error(GenerationError.Validation(offending))
            return
        }

        // Validate the Plandomizer placement (if any) before submitting. An
        // invalid file blocks submission with an inline error.
        val plandomizer = parsePlandomizer() ?: return

        // FAIL FAST (Hard Rule #17): validate the base ROM before any network
        // call. Only OoT 1.0 NTSC-U (CZLE) / NTSC-J (CZLJ), version byte 0, is
        // accepted. This avoids wasting an API request on a seed we cannot patch.
        when (val resolution = resolveBaseRom()) {
            is BaseRomResolution.None -> {
                _generation.value = GenerationState.Error(GenerationError.NoBaseRom)
                return
            }
            is BaseRomResolution.Invalid -> {
                _generation.value = GenerationState.Error(GenerationError.InvalidBaseRom)
                return
            }
            is BaseRomResolution.Accepted -> {
                // Proceed with the accepted base ROM.
                runGenerationWithBaseRom(schema, resolution.rom, apiKey, plandomizer)
            }
        }
    }

    /**
     * Continuation of [runGeneration] after a valid OoT 1.0 base ROM has been
     * resolved. Runs the network + patch pipeline and, on success, persists the
     * resulting seed into [seedRepository] (which moves the ROM into the
     * `Storage.rom(id)` location so it launches through the normal Library path).
     */
    private suspend fun runGenerationWithBaseRom(
        schema: RandomizerSettingsSchema,
        baseRom: BaseRom,
        apiKey: String,
        plandomizer: JSONObject?
    ) {
        val settingsJson = SettingsStateBuilder.build(
            schema = schema,
            values = formValues,
            stripCosmetics = true
        )

        _generation.value = GenerationState.CreatingSeed
        try {
            val created = createSeedWithPlandomizer(
                settingsJson = settingsJson,
                apiKey = apiKey,
                version = selectedVersion.value,
                seed = seedString.value?.takeIf { it.isNotBlank() },
                plandomizer = plandomizer
            )

            val status = SeedPoller.pollUntilDone(
                client = apiClient,
                apiKey = apiKey,
                seedId = created.id,
                onProgress = { progress, queuePos ->
                    _generation.value = GenerationState.Polling(progress, queuePos)
                }
            )
            if (status.status != SeedStatus.STATUS_SUCCESS && status.status != SeedStatus.STATUS_GENERATED_WITH_LINK) {
                _generation.value = GenerationState.Error(GenerationError.Unknown("unexpected status"))
                return
            }

            _generation.value = GenerationState.DownloadingPatch
            val patchDir = File(appContext.cacheDir, "randomizer/patches")
            patchDir.mkdirs()
            val patchFile = File(patchDir, "${created.id}.zpfz")
            apiClient.downloadPatch(apiKey, created.id, patchFile)

            _generation.value = GenerationState.ApplyingPatch
            val outputDir = File(appContext.filesDir, "randomized_roms")
            val outputName = "${FileNameSanitizer.sanitize(seedName.value.ifBlank { created.id })}.z64"
            val romFile = RandomizerPatcherFacade.applySeedPatch(
                baseRom = File(baseRom.path),
                patchFile = patchFile,
                outputDir = outputDir,
                outputName = outputName
            )

            // Persist the seed: move the patched ROM into the repository (which
            // places it at Storage.rom(id)) and record the index entry.
            val entry = buildSeedEntry(created, baseRom, plandomizer != null && !plandomizerRejected)
            seedRepository.add(entry, romFile)

            // Clean up the temp patch on success (kept on failure for retry/debug).
            runCatching { patchFile.delete() }

            if (plandomizerRejected) {
                _generation.value = GenerationState.SuccessWithoutPlandomizer(entry)
            } else {
                _generation.value = GenerationState.Success(entry)
            }
        } catch (e: OotrApiException.MissingApiKey) {
            _generation.value = GenerationState.Error(GenerationError.MissingApiKey)
        } catch (e: OotrApiException.QueueFull) {
            _generation.value = GenerationState.Error(GenerationError.QueueFull)
        } catch (e: OotrApiException.RateLimited) {
            _generation.value = GenerationState.Error(GenerationError.RateLimited)
        } catch (e: OotrApiException.GenerationTimeout) {
            _generation.value = GenerationState.Error(GenerationError.GenerationTimeout)
        } catch (e: OotrApiException.NetworkError) {
            _generation.value = GenerationState.Error(GenerationError.Network)
        } catch (e: RandomizerPatchException) {
            _generation.value = GenerationState.Error(GenerationError.PatchApply(e.message ?: "patch apply failed"))
        } catch (e: OotrApiException) {
            _generation.value = GenerationState.Error(GenerationError.Unknown(e.message ?: "api error"))
        } catch (e: Exception) {
            _generation.value = GenerationState.Error(GenerationError.Unknown(e.message ?: "unknown error"))
        }
    }

    /**
     * Resolve an accepted OoT 1.0 base ROM (Hard Rule #17). Re-reads each
     * imported ROM's header from its actual file (already normalized to z64 at
     * import) and accepts ONLY game code `CZLE`/`CZLJ` with version byte `0`.
     *
     * - [BaseRomResolution.None] when no base ROM is imported at all.
     * - [BaseRomResolution.Invalid] when ROMs exist but none are accepted
     *   (e.g. wrong game, wrong version, or unreadable header).
     * - [BaseRomResolution.Accepted] with the first matching ROM.
     */
    private fun resolveBaseRom(): BaseRomResolution {
        val candidates = baseRomRepository.getAll()
        if (candidates.isEmpty()) return BaseRomResolution.None
        val accepted = candidates.firstOrNull { rom ->
            val header = readNormalizedHeader(File(rom.path))
            header != null && BaseRomValidator.isAccepted(header)
        }
        return if (accepted != null) {
            BaseRomResolution.Accepted(accepted)
        } else {
            BaseRomResolution.Invalid
        }
    }

    /**
     * Read and normalize the N64 header (first 64 bytes) of [file] and parse it.
     * Only the header is normalized (cheap) so we honor the RomNormalizer step
     * without buffering the whole multi-megabyte ROM on the heap.
     */
    private fun readNormalizedHeader(file: File): RomHeader? {
        val head = ByteArray(0x40)
        RandomAccessFile(file, "r").use { raf ->
            if (raf.read(head) < 0x40) return null
        }
        val normalized = runCatching { RomNormalizer.normalize(head) }.getOrNull() ?: return null
        return RomHeader.fromNormalizedZ64(normalized)
    }

    /**
     * Build the [RandomizedSeedEntry] for a successfully generated seed. The id
     * is `ootr_` + a short random token and doubles as the launch hack id (so
     * [br.com.redclaw.zelda64player.repositories.Storage.rom] resolves the ROM).
     */
    private fun buildSeedEntry(
        created: SeedCreateResponse,
        baseRom: BaseRom,
        hasPlandomizer: Boolean
    ): RandomizedSeedEntry {
        val id = "ootr_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val version = created.version.ifBlank { selectedVersion.value ?: "latest" }
        val label = baseRom.displayName.takeIf { it.isNotBlank() }
            ?: "${baseRom.gameCode} v${baseRom.versionByte}"
        return RandomizedSeedEntry(
            id = id,
            name = seedName.value.ifBlank { created.id },
            ootrSeedId = created.id,
            ootrVersion = version,
            createdAt = System.currentTimeMillis(),
            hasPlandomizer = hasPlandomizer,
            romFileName = "rom_$id",
            baseRomLabel = label
        )
    }

    /**
     * Parse and validate the user-provided Plandomizer text.
     *
     * @return The parsed placement [JSONObject] when the text is non-blank and
     *   valid, or `null` when there is no Plandomizer (blank text) or the text
     *   is invalid (in which case [GenerationState.Error] is set to block
     *   submission).
     */
    private fun parsePlandomizer(): JSONObject? {
        val text = plandomizerText.value?.takeIf { it.isNotBlank() } ?: return null
        val result = PlandomizerValidator.validate(text)
        if (!result.valid) {
            _generation.value = GenerationState.Error(
                GenerationError.PlandomizerInvalid(result.errors)
            )
            return null
        }
        return result.parsed
    }

    /**
     * Create a seed, attaching the Plandomizer placement when present.
     *
     * If the API rejects the settings with HTTP 400 **and** a Plandomizer was
     * attached, we retry exactly once without the placement. If that retry
     * succeeds, [plandomizerRejected] is set so the caller can surface a
     * distinct "generated without Plandomizer" state. If the retry also fails,
     * the original exception propagates (normal error path).
     */
    private suspend fun createSeedWithPlandomizer(
        settingsJson: JSONObject,
        apiKey: String,
        version: String?,
        seed: String?,
        plandomizer: JSONObject?
    ): SeedCreateResponse {
        plandomizerRejected = false
        return try {
            apiClient.createSeed(
                settingsJson = settingsJson,
                apiKey = apiKey,
                version = version,
                seed = seed,
                plandomizerJson = plandomizer
            )
        } catch (e: OotrApiException.InvalidSettings) {
            if (plandomizer != null) {
                plandomizerRejected = true
                apiClient.createSeed(
                    settingsJson = settingsJson,
                    apiKey = apiKey,
                    version = version,
                    seed = seed,
                    plandomizerJson = null
                )
            } else {
                throw e
            }
        }
    }

}

/**
 * Outcome of resolving an accepted OoT 1.0 base ROM (Hard Rule #17).
 */
private sealed class BaseRomResolution {
    /** No base ROM imported at all. */
    object None : BaseRomResolution()

    /** ROMs imported but none are an accepted OoT 1.0 NTSC-U/J build. */
    object Invalid : BaseRomResolution()

    /** An accepted base ROM was found. */
    data class Accepted(val rom: BaseRom) : BaseRomResolution()
}

/**
 * State of the seed generation pipeline, surfaced to the UI.
 */
sealed class GenerationState {
    /** Nothing happening yet. */
    object Idle : GenerationState()

    /** Seed creation request in flight. */
    object CreatingSeed : GenerationState()

    /** Polling generation progress. */
    data class Polling(val progress: Int, val queuePosition: Int?) : GenerationState()

    /** Patch download in flight. */
    object DownloadingPatch : GenerationState()

    /** Patch being applied to the base ROM. */
    object ApplyingPatch : GenerationState()

    /** Generation succeeded; the patched ROM is ready to play. */
    data class Success(val entry: RandomizedSeedEntry) : GenerationState()

    /**
     * Generation succeeded but the attached Plandomizer placement was rejected
     * by the API (HTTP 400) and the seed was generated without it. The patched
     * ROM is still ready to play.
     */
    data class SuccessWithoutPlandomizer(val entry: RandomizedSeedEntry) : GenerationState()

    /** Generation failed with a typed cause. */
    data class Error(val error: GenerationError) : GenerationState()
}

/**
 * Typed causes of a generation failure, mapped to user-facing strings by the
 * activity.
 */
sealed class GenerationError {
    /** No OoTR API key configured. */
    object MissingApiKey : GenerationError()

    /** Client-side validation found offending options. */
    data class Validation(val offending: List<String>) : GenerationError()

    /** No base ROM imported at all (need OoT 1.0 NTSC-U/J). */
    object NoBaseRom : GenerationError()

    /** Base ROMs imported but none are an accepted OoT 1.0 NTSC-U/J build. */
    object InvalidBaseRom : GenerationError()

    /** Network failure (no response / IO error). */
    object Network : GenerationError()

    /** Server returned HTTP 429. */
    object RateLimited : GenerationError()

    /** Server returned HTTP 423 (queue full). */
    object QueueFull : GenerationError()

    /** Polling exceeded the overall timeout. */
    object GenerationTimeout : GenerationError()

    /** Patch application failed (corrupt patch / wrong base ROM). */
    data class PatchApply(val detail: String) : GenerationError()

    /** The Plandomizer placement JSON is invalid (blocks submission). */
    data class PlandomizerInvalid(val errors: List<String>) : GenerationError()

    /** Any other failure. */
    data class Unknown(val message: String) : GenerationError()
}
