package br.com.redclaw.zelda64player.randomizer.ui

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.randomizer.settings.SchemaChoice
import br.com.redclaw.zelda64player.randomizer.settings.SchemaOption
import br.com.redclaw.zelda64player.randomizer.settings.SchemaOptionType
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

/**
 * Renders the flattened settings rows produced by [SettingsFormRenderer] into a
 * single [RecyclerView]. The adapter is stateless with respect to form state:
 * it reads the current value of each option from [values] and reports changes
 * through [onValueChanged], which the ViewModel persists as the single source
 * of truth.
 *
 * Supported row types: category header, boolean switch, enum dropdown, integer
 * slider, free-text string, and multi-select list (rendered via a dialog).
 */
class SettingsOptionAdapter(
    private val rows: List<SettingsFormRenderer.SettingsRow>,
    private val values: MutableMap<String, Any?>,
    private val onValueChanged: (name: String, value: Any?) -> Unit,
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_BOOL = 1
        private const val TYPE_ENUM = 2
        private const val TYPE_INT = 3
        private const val TYPE_STRING = 4
        private const val TYPE_LIST = 5
    }

    override fun getItemViewType(position: Int): Int = when (val row = rows[position]) {
        is SettingsFormRenderer.SettingsRow.Header -> TYPE_HEADER
        is SettingsFormRenderer.SettingsRow.OptionRow -> when (row.option.type) {
            SchemaOptionType.BOOL -> TYPE_BOOL
            SchemaOptionType.ENUM -> TYPE_ENUM
            SchemaOptionType.INT -> TYPE_INT
            SchemaOptionType.STRING -> TYPE_STRING
            SchemaOptionType.LIST -> TYPE_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.row_setting_header, parent, false))
            TYPE_BOOL -> BoolVH(inflater.inflate(R.layout.row_setting_bool, parent, false))
            TYPE_ENUM -> EnumVH(inflater.inflate(R.layout.row_setting_enum, parent, false))
            TYPE_INT -> IntVH(inflater.inflate(R.layout.row_setting_int, parent, false))
            TYPE_STRING -> StringVH(inflater.inflate(R.layout.row_setting_string, parent, false))
            TYPE_LIST -> ListVH(inflater.inflate(R.layout.row_setting_list, parent, false))
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is SettingsFormRenderer.SettingsRow.Header -> (holder as HeaderVH).bind(row)
            is SettingsFormRenderer.SettingsRow.OptionRow -> bindOption(holder, row.option)
        }
    }

    override fun getItemCount(): Int = rows.size

    private fun bindOption(holder: RecyclerView.ViewHolder, option: SchemaOption) {
        when (option.type) {
            SchemaOptionType.BOOL -> (holder as BoolVH).bind(option, values, onValueChanged)
            SchemaOptionType.ENUM -> (holder as EnumVH).bind(option, values, onValueChanged, context)
            SchemaOptionType.INT -> (holder as IntVH).bind(option, values, onValueChanged)
            SchemaOptionType.STRING -> (holder as StringVH).bind(option, values, onValueChanged)
            SchemaOptionType.LIST -> (holder as ListVH).bind(option, values, onValueChanged, context)
        }
    }

    // --- View holders -------------------------------------------------------

    class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.header_title)
        fun bind(row: SettingsFormRenderer.SettingsRow.Header) {
            title.text = row.title
        }
    }

    class BoolVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.label)
        private val switch: SwitchMaterial = itemView.findViewById(R.id.switch_value)
        fun bind(
            option: SchemaOption,
            values: MutableMap<String, Any?>,
            onValueChanged: (String, Any?) -> Unit
        ) {
            label.text = option.label
            val current = values[option.name] as? Boolean ?: false
            switch.isChecked = current
            switch.setOnCheckedChangeListener { _, isChecked ->
                onValueChanged(option.name, isChecked)
            }
            option.tooltip?.let { tip ->
                itemView.setOnLongClickListener {
                    Toast.makeText(itemView.context, tip, Toast.LENGTH_LONG).show()
                    true
                }
            }
        }
    }

    class EnumVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.label)
        private val dropdown: MaterialAutoCompleteTextView =
            itemView.findViewById(R.id.enum_value)
        private var option: SchemaOption? = null

        fun bind(
            option: SchemaOption,
            values: MutableMap<String, Any?>,
            onValueChanged: (String, Any?) -> Unit,
            context: Context
        ) {
            this.option = option
            label.text = option.label
            val labels = option.choices.map { it.label }
            dropdown.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, labels))
            val currentValue = values[option.name] as? String ?: option.choices.firstOrNull()?.value ?: ""
            dropdown.setText(option.choices.firstOrNull { it.value == currentValue }?.label ?: "", false)
            dropdown.setOnItemClickListener { _, _, pos, _ ->
                val value = option.choices.getOrNull(pos)?.value ?: return@setOnItemClickListener
                onValueChanged(option.name, value)
            }
            option.tooltip?.let { tip ->
                label.setOnLongClickListener {
                    Toast.makeText(itemView.context, tip, Toast.LENGTH_LONG).show()
                    true
                }
            }
        }
    }

    class IntVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.label)
        private val valueText: TextView = itemView.findViewById(R.id.int_value)
        private val slider: Slider = itemView.findViewById(R.id.int_slider)
        fun bind(
            option: SchemaOption,
            values: MutableMap<String, Any?>,
            onValueChanged: (String, Any?) -> Unit
        ) {
            label.text = option.label
            val min = (option.min ?: 0).toFloat()
            val max = (option.max ?: 100).toFloat()
            val step = (option.step ?: 1).toFloat().coerceAtLeast(1f)
            slider.valueFrom = min
            slider.valueTo = max
            slider.stepSize = step
            val current = (values[option.name] as? Number)?.toInt() ?: option.min ?: 0
            slider.value = current.toFloat().coerceIn(min, max)
            valueText.text = current.toString()
            slider.addOnChangeListener { _, value, _ ->
                val intVal = value.toInt()
                valueText.text = intVal.toString()
                onValueChanged(option.name, intVal)
            }
            option.tooltip?.let { tip ->
                label.setOnLongClickListener {
                    Toast.makeText(itemView.context, tip, Toast.LENGTH_LONG).show()
                    true
                }
            }
        }
    }

    class StringVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.label)
        private val edit: TextInputEditText = itemView.findViewById(R.id.string_value)
        private var option: SchemaOption? = null
        private var watcher: TextWatcher? = null
        fun bind(
            option: SchemaOption,
            values: MutableMap<String, Any?>,
            onValueChanged: (String, Any?) -> Unit
        ) {
            this.option = option
            label.text = option.label
            val current = values[option.name] as? String ?: ""
            if (edit.text?.toString() != current) edit.setText(current)
            watcher?.let { edit.removeTextChangedListener(it) }
            watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onValueChanged(option.name, s?.toString() ?: "")
                }
            }
            edit.addTextChangedListener(watcher)
            option.tooltip?.let { tip ->
                label.setOnLongClickListener {
                    Toast.makeText(itemView.context, tip, Toast.LENGTH_LONG).show()
                    true
                }
            }
        }
    }

    class ListVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.label)
        private val button: com.google.android.material.button.MaterialButton =
            itemView.findViewById(R.id.list_button)
        private val summary: TextView = itemView.findViewById(R.id.list_summary)
        private var option: SchemaOption? = null

        fun bind(
            option: SchemaOption,
            values: MutableMap<String, Any?>,
            onValueChanged: (String, Any?) -> Unit,
            context: Context
        ) {
            this.option = option
            label.text = option.label
            button.setText(R.string.randomizer_edit)
            val selected = currentSelection(values, option)
            updateSummary(selected)
            button.setOnClickListener {
                openPicker(option, values, onValueChanged, context, selected.toMutableList())
            }
            option.tooltip?.let { tip ->
                label.setOnLongClickListener {
                    Toast.makeText(itemView.context, tip, Toast.LENGTH_LONG).show()
                    true
                }
            }
        }

        private fun currentSelection(values: MutableMap<String, Any?>, option: SchemaOption): List<String> {
            @Suppress("UNCHECKED_CAST")
            return (values[option.name] as? List<Any?>)?.mapNotNull { it?.toString() }
                ?: emptyList()
        }

        private fun updateSummary(selected: List<String>) {
            if (selected.isEmpty()) {
                summary.setText(R.string.randomizer_list_none)
            } else {
                val labels = option?.choices
                    ?.filter { selected.contains(it.value) }
                    ?.joinToString { it.label }
                    ?: selected.joinToString()
                summary.text = labels
            }
        }

        private fun openPicker(
            option: SchemaOption,
            values: MutableMap<String, Any?>,
            onValueChanged: (String, Any?) -> Unit,
            context: Context,
            selected: MutableList<String>
        ) {
            val choices: List<SchemaChoice> = option.choices
            val labels = choices.map { it.label }.toTypedArray()
            val checked = BooleanArray(choices.size) { i -> selected.contains(choices[i].value) }
            AlertDialog.Builder(context)
                .setTitle(option.label)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    val value = choices[which].value
                    if (isChecked) selected.add(value) else selected.remove(value)
                }
                .setPositiveButton(android.R.string.ok) {
                    _, _ ->
                    onValueChanged(option.name, selected.toList())
                    updateSummary(selected)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
