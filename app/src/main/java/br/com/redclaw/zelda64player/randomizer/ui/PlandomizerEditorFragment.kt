package br.com.redclaw.zelda64player.randomizer.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.FragmentPlandomizerEditorBinding
import br.com.redclaw.zelda64player.randomizer.settings.PLANDOMIZER_DUNGEON_VALUES
import br.com.redclaw.zelda64player.randomizer.settings.PLANDOMIZER_ITEM_POOL_TYPES
import br.com.redclaw.zelda64player.randomizer.settings.PLANDOMIZER_TRIAL_VALUES
import br.com.redclaw.zelda64player.randomizer.settings.PlandomizerBuilderState
import br.com.redclaw.zelda64player.randomizer.settings.PlandomizerEntranceRow
import br.com.redclaw.zelda64player.randomizer.settings.PlandomizerDungeonRow
import br.com.redclaw.zelda64player.randomizer.settings.PlandomizerItemPoolRow
import br.com.redclaw.zelda64player.randomizer.settings.PlandomizerRow
import br.com.redclaw.zelda64player.randomizer.settings.PlandomizerTrialRow
import br.com.redclaw.zelda64player.randomizer.settings.PlandomizerValidator
import br.com.redclaw.zelda64player.randomizer.settings.ValidationResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Plandomizer editor: a bottom sheet with two tabs.
 *
 *  - **Texto**: a full-width monospace [EditText] holding the raw placement
 *    JSON (the single source of truth). Supports importing a `.json` file via
 *    the Storage Access Framework, validating inline, and clearing.
 *  - **Construtor**: a form-based builder that constructs the same JSON from
 *    dynamic rows (locations, entrances, dungeons, trials, starting items,
 *    item pool, settings overrides, file hash). "Gerar JSON" serializes the
 *    form into the text tab.
 *
 * Switching to the builder tab re-parses the text; if it is invalid the user
 * is kept on the text tab with an error. The [RandomizerViewModel] always
 * reflects the latest text via [RandomizerViewModel.plandomizerText].
 */
class PlandomizerEditorFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPlandomizerEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RandomizerViewModel by lazy {
        (requireActivity() as RandomizerActivity).viewModel
    }

    // Mutable lists shared (by reference) with the RecyclerView adapters.
    private val locationsList = mutableListOf<PlandomizerRow>()
    private val entrancesList = mutableListOf<PlandomizerEntranceRow>()
    private val dungeonsList = mutableListOf<PlandomizerDungeonRow>()
    private val trialsList = mutableListOf<PlandomizerTrialRow>()
    private val startingItemsList = mutableListOf<PlandomizerRow>()
    private val itemPoolList = mutableListOf<PlandomizerItemPoolRow>()
    private val settingsList = mutableListOf<PlandomizerRow>()

    private lateinit var locationsAdapter: KvAdapter
    private lateinit var entrancesAdapter: EntranceAdapter
    private lateinit var dungeonsAdapter: DungeonAdapter
    private lateinit var trialsAdapter: TrialAdapter
    private lateinit var startingItemsAdapter: KvAdapter
    private lateinit var itemPoolAdapter: ItemPoolAdapter
    private lateinit var settingsAdapter: KvAdapter

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: ""
            binding.plandoText.setText(text)
            viewModel.plandomizerText.value = text.takeIf { it.isNotBlank() }
            Toast.makeText(
                requireContext(),
                R.string.plandomizer_import_success,
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.plandomizer_import_error, e.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlandomizerEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupTextEditor()
        setupBuilder()
        setupApply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.9).toInt()
        )
    }

    private fun setupTabs() {
        binding.plandoTabs.addTab(
            binding.plandoTabs.newTab().setText(R.string.plandomizer_tab_text)
        )
        binding.plandoTabs.addTab(
            binding.plandoTabs.newTab().setText(R.string.plandomizer_tab_builder)
        )
        binding.plandoTabs.addOnTabSelectedListener(
            object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    if (tab.position == 0) showTextPanel() else showBuilderPanel()
                }
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            }
        )
        // Ensure the text panel is the initial view (it is visible by default in
        // the layout, but selecting the tab makes the state deterministic).
        binding.plandoTabs.getTabAt(0)?.select()
    }

    private fun showTextPanel() {
        binding.plandoTextPanel.visibility = View.VISIBLE
        binding.plandoBuilderPanel.visibility = View.GONE
    }

    private fun showBuilderPanel() {
        // Re-parse the text into the builder; if invalid, stay on the text tab.
        if (!loadBuilderFromText()) return
        binding.plandoTextPanel.visibility = View.GONE
        binding.plandoBuilderPanel.visibility = View.VISIBLE
    }

    private fun setupTextEditor() {
        val initial = viewModel.plandomizerText.value
        binding.plandoText.setText(initial ?: "")
        binding.plandoText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.plandomizerText.value = s?.toString()?.takeIf { it.isNotBlank() }
            }
        })

        binding.plandoImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
        }
        binding.plandoValidate.setOnClickListener {
            showValidation(PlandomizerValidator.validate(binding.plandoText.text.toString()))
        }
        binding.plandoClear.setOnClickListener {
            binding.plandoText.setText("")
            viewModel.plandomizerText.value = null
            binding.plandoValidation.text = ""
        }
    }

    private fun setupBuilder() {
        locationsAdapter = KvAdapter(locationsList) { locationsAdapter.removeAt(it) }
        entrancesAdapter = EntranceAdapter(entrancesList) { entrancesAdapter.removeAt(it) }
        dungeonsAdapter = DungeonAdapter(dungeonsList) { dungeonsAdapter.removeAt(it) }
        trialsAdapter = TrialAdapter(trialsList) { trialsAdapter.removeAt(it) }
        startingItemsAdapter = KvAdapter(startingItemsList) { startingItemsAdapter.removeAt(it) }
        itemPoolAdapter = ItemPoolAdapter(itemPoolList) { itemPoolAdapter.removeAt(it) }
        settingsAdapter = KvAdapter(settingsList) { settingsAdapter.removeAt(it) }

        binding.recyclerLocations.adapter = locationsAdapter
        binding.recyclerEntrances.adapter = entrancesAdapter
        binding.recyclerDungeons.adapter = dungeonsAdapter
        binding.recyclerTrials.adapter = trialsAdapter
        binding.recyclerStartingItems.adapter = startingItemsAdapter
        binding.recyclerItemPool.adapter = itemPoolAdapter
        binding.recyclerSettings.adapter = settingsAdapter

        listOf(
            binding.recyclerLocations, binding.recyclerEntrances, binding.recyclerDungeons,
            binding.recyclerTrials, binding.recyclerStartingItems, binding.recyclerItemPool,
            binding.recyclerSettings
        ).forEach {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.isNestedScrollingEnabled = false
        }

        binding.btnAddLocation.setOnClickListener { locationsAdapter.add() }
        binding.btnAddEntrance.setOnClickListener { entrancesAdapter.add() }
        binding.btnAddDungeon.setOnClickListener { dungeonsAdapter.add() }
        binding.btnAddTrial.setOnClickListener { trialsAdapter.add() }
        binding.btnAddStartingItem.setOnClickListener { startingItemsAdapter.add() }
        binding.btnAddItemPool.setOnClickListener { itemPoolAdapter.add() }
        binding.btnAddSetting.setOnClickListener { settingsAdapter.add() }

        binding.btnGenerateJson.setOnClickListener { generateJsonFromBuilder() }

        // Initialize the builder from any valid existing text (silent).
        val initial = viewModel.plandomizerText.value
        if (!initial.isNullOrBlank()) {
            val res = PlandomizerValidator.validate(initial)
            if (res.valid && res.parsed != null) {
                resetBuilderState(PlandomizerBuilderState.fromJson(res.parsed))
            }
        }
    }

    private fun setupApply() {
        binding.plandoApply.setOnClickListener {
            viewModel.plandomizerText.value =
                binding.plandoText.text.toString().takeIf { it.isNotBlank() }
            dismiss()
        }
    }

    private fun generateJsonFromBuilder() {
        val state = collectBuilderState()
        val json = state.toJson()
        val pretty = json.toString(2)
        binding.plandoText.setText(pretty)
        viewModel.plandomizerText.value = pretty
        binding.plandoTabs.getTabAt(0)?.select()
        Toast.makeText(
            requireContext(),
            R.string.plandomizer_generated,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun collectBuilderState(): PlandomizerBuilderState = PlandomizerBuilderState(
        locations = locationsList.toList(),
        entrances = entrancesList.toList(),
        dungeons = dungeonsList.toList(),
        trials = trialsList.toList(),
        startingItems = startingItemsList.toList(),
        itemPool = itemPoolList.toList(),
        settings = settingsList.toList(),
        fileHash = listOf(
            binding.editHash1.text.toString(),
            binding.editHash2.text.toString(),
            binding.editHash3.text.toString(),
            binding.editHash4.text.toString(),
            binding.editHash5.text.toString()
        )
    )

    private fun loadBuilderFromText(): Boolean {
        val text = binding.plandoText.text.toString()
        if (text.isBlank()) {
            resetBuilderState(PlandomizerBuilderState())
            return true
        }
        val result = PlandomizerValidator.validate(text)
        if (!result.valid || result.parsed == null) {
            showValidation(result)
            binding.plandoTabs.getTabAt(0)?.select()
            return false
        }
        resetBuilderState(PlandomizerBuilderState.fromJson(result.parsed))
        return true
    }

    private fun resetBuilderState(state: PlandomizerBuilderState) {
        locationsList.clear(); locationsList.addAll(state.locations)
        entrancesList.clear(); entrancesList.addAll(state.entrances)
        dungeonsList.clear(); dungeonsList.addAll(state.dungeons)
        trialsList.clear(); trialsList.addAll(state.trials)
        startingItemsList.clear(); startingItemsList.addAll(state.startingItems)
        itemPoolList.clear(); itemPoolList.addAll(state.itemPool)
        settingsList.clear(); settingsList.addAll(state.settings)
        locationsAdapter.notifyDataSetChanged()
        entrancesAdapter.notifyDataSetChanged()
        dungeonsAdapter.notifyDataSetChanged()
        trialsAdapter.notifyDataSetChanged()
        startingItemsAdapter.notifyDataSetChanged()
        itemPoolAdapter.notifyDataSetChanged()
        settingsAdapter.notifyDataSetChanged()
        binding.editHash1.setText(state.fileHash.getOrNull(0) ?: "")
        binding.editHash2.setText(state.fileHash.getOrNull(1) ?: "")
        binding.editHash3.setText(state.fileHash.getOrNull(2) ?: "")
        binding.editHash4.setText(state.fileHash.getOrNull(3) ?: "")
        binding.editHash5.setText(state.fileHash.getOrNull(4) ?: "")
    }

    private fun showValidation(result: ValidationResult) {
        val tv = binding.plandoValidation
        when {
            binding.plandoText.text.isBlank() -> {
                tv.setTextColor(requireContext().getColor(R.color.color_on_surface_variant))
                tv.setText(R.string.plandomizer_empty)
            }
            result.valid && result.warnings.isEmpty() -> {
                tv.setTextColor(requireContext().getColor(R.color.color_primary))
                tv.setText(R.string.plandomizer_valid)
            }
            result.valid -> {
                tv.setTextColor(requireContext().getColor(R.color.color_secondary))
                tv.text = getString(R.string.plandomizer_warnings, result.warnings.joinToString("; "))
            }
            else -> {
                tv.setTextColor(requireContext().getColor(R.color.color_error))
                tv.text = getString(R.string.plandomizer_invalid, result.errors.joinToString("; "))
            }
        }
    }

    companion object {
        const val TAG = "PlandomizerEditorFragment"

        fun newInstance(): PlandomizerEditorFragment = PlandomizerEditorFragment()
    }
}

// ---------------------------------------------------------------------------
// RecyclerView adapters for the Plandomizer builder rows.
// Each adapter owns a mutable list (shared by reference with the fragment) and
// updates the model on every text/spinner change so "Gerar JSON" can read the
// current state directly.
// ---------------------------------------------------------------------------

private fun bindSpinner(
    spinner: Spinner,
    values: List<String>,
    current: String,
    onSelected: (String) -> Unit
) {
    val adapter = ArrayAdapter(
        spinner.context,
        android.R.layout.simple_spinner_item,
        values
    )
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.adapter = adapter
    spinner.setSelection(values.indexOf(current).coerceAtLeast(0), false)
    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
            onSelected(values[pos])
        }
        override fun onNothingSelected(parent: AdapterView<*>) {}
    }
}

/** Key/value rows (locations, starting items, settings overrides). */
private class KvAdapter(
    private val items: MutableList<PlandomizerRow>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<KvAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val key: android.widget.EditText = view.findViewById(R.id.row_key)
        val value: android.widget.EditText = view.findViewById(R.id.row_value)
        val remove: android.widget.ImageButton = view.findViewById(R.id.row_remove)
        var keyWatcher: TextWatcher? = null
        var valueWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plando_kv, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.key.removeTextChangedListener(holder.keyWatcher)
        holder.value.removeTextChangedListener(holder.valueWatcher)
        holder.key.setText(item.key)
        holder.value.setText(item.value)
        holder.keyWatcher = simpleWatcher { s ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(key = s)
        }
        holder.valueWatcher = simpleWatcher { s ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(value = s)
        }
        holder.key.addTextChangedListener(holder.keyWatcher)
        holder.value.addTextChangedListener(holder.valueWatcher)
        holder.remove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size

    fun add() {
        items.add(PlandomizerRow("", ""))
        notifyItemInserted(items.size - 1)
    }

    fun removeAt(pos: Int) {
        if (pos in items.indices) {
            items.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }
}

/** Entrance rows (from -> to). */
private class EntranceAdapter(
    private val items: MutableList<PlandomizerEntranceRow>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<EntranceAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val from: android.widget.EditText = view.findViewById(R.id.row_from)
        val to: android.widget.EditText = view.findViewById(R.id.row_to)
        val remove: android.widget.ImageButton = view.findViewById(R.id.row_remove)
        var fromWatcher: TextWatcher? = null
        var toWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plando_entrance, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.from.removeTextChangedListener(holder.fromWatcher)
        holder.to.removeTextChangedListener(holder.toWatcher)
        holder.from.setText(item.from)
        holder.to.setText(item.to)
        holder.fromWatcher = simpleWatcher { s ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(from = s)
        }
        holder.toWatcher = simpleWatcher { s ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(to = s)
        }
        holder.from.addTextChangedListener(holder.fromWatcher)
        holder.to.addTextChangedListener(holder.toWatcher)
        holder.remove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size

    fun add() {
        items.add(PlandomizerEntranceRow("", ""))
        notifyItemInserted(items.size - 1)
    }

    fun removeAt(pos: Int) {
        if (pos in items.indices) {
            items.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }
}

/** Dungeon rows (name + vanilla/mq/random). */
private class DungeonAdapter(
    private val items: MutableList<PlandomizerDungeonRow>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<DungeonAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: android.widget.EditText = view.findViewById(R.id.row_name)
        val mode: Spinner = view.findViewById(R.id.row_mode)
        val remove: android.widget.ImageButton = view.findViewById(R.id.row_remove)
        var nameWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plando_dungeon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.removeTextChangedListener(holder.nameWatcher)
        holder.name.setText(item.name)
        holder.nameWatcher = simpleWatcher { s ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(name = s)
        }
        holder.name.addTextChangedListener(holder.nameWatcher)
        bindSpinner(holder.mode, PLANDOMIZER_DUNGEON_VALUES, item.mode) { mode ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(mode = mode)
        }
        holder.remove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size

    fun add() {
        items.add(PlandomizerDungeonRow("", "vanilla"))
        notifyItemInserted(items.size - 1)
    }

    fun removeAt(pos: Int) {
        if (pos in items.indices) {
            items.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }
}

/** Trial rows (name + active/inactive/random). */
private class TrialAdapter(
    private val items: MutableList<PlandomizerTrialRow>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<TrialAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: android.widget.EditText = view.findViewById(R.id.row_name)
        val mode: Spinner = view.findViewById(R.id.row_mode)
        val remove: android.widget.ImageButton = view.findViewById(R.id.row_remove)
        var nameWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plando_trial, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.removeTextChangedListener(holder.nameWatcher)
        holder.name.setText(item.name)
        holder.nameWatcher = simpleWatcher { s ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(name = s)
        }
        holder.name.addTextChangedListener(holder.nameWatcher)
        bindSpinner(holder.mode, PLANDOMIZER_TRIAL_VALUES, item.mode) { mode ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(mode = mode)
        }
        holder.remove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size

    fun add() {
        items.add(PlandomizerTrialRow("", "active"))
        notifyItemInserted(items.size - 1)
    }

    fun removeAt(pos: Int) {
        if (pos in items.indices) {
            items.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }
}

/** Item-pool rows (item + type + count). */
private class ItemPoolAdapter(
    private val items: MutableList<PlandomizerItemPoolRow>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ItemPoolAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val item: android.widget.EditText = view.findViewById(R.id.row_item)
        val type: Spinner = view.findViewById(R.id.row_type)
        val count: android.widget.EditText = view.findViewById(R.id.row_count)
        val remove: android.widget.ImageButton = view.findViewById(R.id.row_remove)
        var itemWatcher: TextWatcher? = null
        var countWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plando_item_pool, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.item.removeTextChangedListener(holder.itemWatcher)
        holder.count.removeTextChangedListener(holder.countWatcher)
        holder.item.setText(item.item)
        holder.count.setText(item.count)
        holder.itemWatcher = simpleWatcher { s ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(item = s)
        }
        holder.countWatcher = simpleWatcher { s ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(count = s)
        }
        holder.item.addTextChangedListener(holder.itemWatcher)
        holder.count.addTextChangedListener(holder.countWatcher)
        bindSpinner(holder.type, PLANDOMIZER_ITEM_POOL_TYPES, item.type) { type ->
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) items[p] = items[p].copy(type = type)
        }
        holder.remove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size

    fun add() {
        items.add(PlandomizerItemPoolRow("", "set", "1"))
        notifyItemInserted(items.size - 1)
    }

    fun removeAt(pos: Int) {
        if (pos in items.indices) {
            items.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }
}

/** Build a [TextWatcher] that forwards the trimmed text to [onChanged]. */
private fun simpleWatcher(onChanged: (String) -> Unit): TextWatcher =
    object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            onChanged(s?.toString() ?: "")
        }
    }
