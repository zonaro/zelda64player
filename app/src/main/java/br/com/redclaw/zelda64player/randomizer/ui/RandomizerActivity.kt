package br.com.redclaw.zelda64player.randomizer.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ActivityRandomizerBinding
import br.com.redclaw.zelda64player.randomizer.settings.RandomizerSettingsSchema
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Main Randomizer entry point. Loads the settings schema, renders a
 * schema-driven form (category tabs + dynamic rows), and runs the generation
 * pipeline through [RandomizerViewModel].
 *
 * The form is fully schema-driven: every option type (bool / enum / int /
 * string / list) is rendered by [SettingsOptionAdapter] from the parsed asset,
 * so adding or changing options requires no code changes here.
 */
class RandomizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomizerBinding
    internal lateinit var viewModel: RandomizerViewModel
    private lateinit var adapter: SettingsOptionAdapter
    private var categoryPositions: Map<String, Int> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.randomizerToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.randomizer_title)
        binding.randomizerToolbar.setNavigationOnClickListener { finish() }

        viewModel = ViewModelProvider(this)[RandomizerViewModel::class.java]

        binding.seedNameEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.seedName.value = s?.toString()?.take(60) ?: ""
            }
        })
        binding.seedStringEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.seedString.value = s?.toString()?.takeIf { it.isNotBlank() }
            }
        })

        lifecycleScope.launch {
            viewModel.availableVersions.collect { versions ->
                if (versions.isEmpty()) {
                    binding.versionLayout.visibility = View.GONE
                } else {
                    binding.versionLayout.visibility = View.VISIBLE
                    binding.versionDropdown.setAdapter(
                        ArrayAdapter<String>(
                            this@RandomizerActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            versions
                        )
                    )
                    val selected = viewModel.selectedVersion.value ?: versions.first()
                    binding.versionDropdown.setText(selected, false)
                }
            }
        }
        binding.versionDropdown.setOnItemClickListener { _, _, pos, _ ->
            viewModel.availableVersions.value.getOrNull(pos)?.let {
                viewModel.selectedVersion.value = it
            }
        }

        // Plandomizer editor entry point (Phase R4).
        binding.plandomizerButton.setOnClickListener {
            PlandomizerEditorFragment.newInstance()
                .show(supportFragmentManager, PlandomizerEditorFragment.TAG)
        }
        lifecycleScope.launch {
            viewModel.plandomizerText.collect { text ->
                val hasPlando = !text.isNullOrBlank()
                binding.plandomizerButton.isEnabled = true
                binding.plandomizerButton.alpha = 1.0f
                binding.plandomizerButton.setText(
                    if (hasPlando) R.string.randomizer_plandomizer_configured
                    else R.string.randomizer_plandomizer_button
                )
            }
        }

        lifecycleScope.launch {
            viewModel.schema.collect { schema ->
                if (schema != null) setupForm(schema)
            }
        }

        lifecycleScope.launch {
            viewModel.generation.collect { renderGeneration(it) }
        }

        binding.generateButton.setOnClickListener { onGenerateClicked() }
    }

    private fun onGenerateClicked() {
        if (viewModel.seedName.value.isBlank()) {
            binding.seedNameLayout.error = getString(R.string.randomizer_seed_name_required)
            binding.seedNameEdit.requestFocus()
            return
        }
        binding.seedNameLayout.error = null
        viewModel.generate()
    }

    private fun setupForm(schema: RandomizerSettingsSchema) {
        val titleFor: (String) -> String = { id ->
            val resId = resources.getIdentifier("randomizer_category_$id", "string", packageName)
            if (resId != 0) getString(resId) else id
        }
        val rows = SettingsFormRenderer.buildRows(schema, titleFor)
        categoryPositions = SettingsFormRenderer.categoryPositions(schema)
        adapter = SettingsOptionAdapter(
            rows = rows,
            values = viewModel.formValues,
            onValueChanged = { name, value -> viewModel.setValue(name, value) },
            context = this
        )
        binding.optionsRecycler.layoutManager = LinearLayoutManager(this)
        binding.optionsRecycler.adapter = adapter

        binding.categoryTabs.removeAllTabs()
        schema.categories.forEach { cat ->
            binding.categoryTabs.addTab(binding.categoryTabs.newTab().setText(titleFor(cat.id)))
        }
        binding.categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val id = schema.categories.getOrNull(tab.position)?.id ?: return
                val pos = categoryPositions[id] ?: return
                binding.optionsRecycler.scrollToPosition(pos)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun renderGeneration(state: GenerationState) {
        when (state) {
            is GenerationState.Idle -> {
                binding.randomizerProgress.visibility = View.GONE
                binding.statusText.visibility = View.GONE
                binding.generateButton.isEnabled = true
                binding.generateButton.setText(R.string.randomizer_generate)
            }
            is GenerationState.CreatingSeed,
            is GenerationState.DownloadingPatch,
            is GenerationState.ApplyingPatch -> {
                binding.randomizerProgress.visibility = View.VISIBLE
                binding.randomizerProgress.isIndeterminate = true
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.setText(statusTextFor(state))
                binding.generateButton.isEnabled = false
            }
            is GenerationState.Polling -> {
                binding.randomizerProgress.visibility = View.VISIBLE
                binding.randomizerProgress.isIndeterminate = false
                binding.randomizerProgress.progress = state.progress.coerceIn(0, 100)
                binding.statusText.visibility = View.VISIBLE
                val queue = state.queuePosition?.let { "  ${getString(R.string.randomizer_queue_position, it)}" } ?: ""
                binding.statusText.text = "${getString(R.string.randomizer_polling, state.progress)}$queue"
                binding.generateButton.isEnabled = false
            }
            is GenerationState.Success -> {
                binding.randomizerProgress.visibility = View.GONE
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.setText(R.string.randomizer_success)
                binding.generateButton.isEnabled = true
                binding.generateButton.setText(R.string.randomizer_generate)
                Toast.makeText(
                    this,
                    getString(R.string.randomizer_success_detail, state.entry.ootrSeedId),
                    Toast.LENGTH_LONG
                ).show()
            }
            is GenerationState.SuccessWithoutPlandomizer -> {
                binding.randomizerProgress.visibility = View.GONE
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.setText(R.string.randomizer_success_plandomizer_rejected)
                binding.generateButton.isEnabled = true
                binding.generateButton.setText(R.string.randomizer_generate)
                Toast.makeText(
                    this,
                    getString(R.string.randomizer_success_detail, state.entry.ootrSeedId),
                    Toast.LENGTH_LONG
                ).show()
            }
            is GenerationState.Error -> {
                binding.randomizerProgress.visibility = View.GONE
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text =
                    "${getString(R.string.randomizer_error_prefix)} ${mapError(state.error)}"
                binding.generateButton.isEnabled = true
                binding.generateButton.setText(R.string.randomizer_generate)
            }
        }
    }

    private fun statusTextFor(state: GenerationState): Int = when (state) {
        is GenerationState.CreatingSeed -> R.string.randomizer_status_creating
        is GenerationState.DownloadingPatch -> R.string.randomizer_status_downloading
        is GenerationState.ApplyingPatch -> R.string.randomizer_status_applying
        else -> R.string.randomizer_status_working
    }

    private fun mapError(error: GenerationError): String = when (error) {
        is GenerationError.MissingApiKey -> getString(R.string.randomizer_error_missing_key)
        is GenerationError.Validation ->
            getString(R.string.randomizer_error_validation, error.offending.size)
        is GenerationError.NoBaseRom -> getString(R.string.randomizer_error_no_base_rom)
        is GenerationError.InvalidBaseRom -> getString(R.string.randomizer_error_invalid_base_rom)
        is GenerationError.Network -> getString(R.string.randomizer_error_network)
        is GenerationError.RateLimited -> getString(R.string.randomizer_error_rate_limited)
        is GenerationError.QueueFull -> getString(R.string.randomizer_error_queue_full)
        is GenerationError.GenerationTimeout -> getString(R.string.randomizer_error_timeout)
        is GenerationError.PatchApply -> getString(R.string.randomizer_error_patch, error.detail)
        is GenerationError.PlandomizerInvalid ->
            getString(R.string.randomizer_error_plandomizer_invalid, error.errors.firstOrNull() ?: "")
        is GenerationError.Unknown -> error.message
    }
}
