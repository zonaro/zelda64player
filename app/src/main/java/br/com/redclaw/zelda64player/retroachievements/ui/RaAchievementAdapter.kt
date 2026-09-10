package br.com.redclaw.zelda64player.retroachievements.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ItemRaAchievementBinding
import br.com.redclaw.zelda64player.databinding.ItemRaAchievementGridBinding
import br.com.redclaw.zelda64player.databinding.ItemRaSectionHeaderBinding
import br.com.redclaw.zelda64player.retroachievements.data.RaAchievementDef
import coil.load

/**
 * Stable list item rendered by [RaAchievementAdapter]. Two concrete kinds exist: a per-game
 * [RaSectionItem] header and an [RaAchievementRow] for one achievement. The [key] provides DiffUtil
 * a stable identity so section headers and achievement rows are never confused across list updates.
 */
sealed interface RaListItem {
    val key: Any
}

/** Display model: definition + live unlock state. */
data class RaAchievementRow(val def: RaAchievementDef, val unlocked: Boolean) : RaListItem {
    override val key: Any
        get() = "ach:${def.id}"
}

/** Per-game section header shown before a game's achievement rows. */
data class RaSectionItem(
        val gameId: Long,
        val title: String,
        val unlockedCount: Int,
        val totalCount: Int,
        val earnedPoints: Int,
        val totalPoints: Int
) : RaListItem {
    override val key: Any
        get() = "section:$gameId"
}

/** Grid-mode cell: badge-only thumbnail (span 1). */
data class RaGridCell(val row: RaAchievementRow) : RaListItem {
    override val key: Any
        get() = "grid:${row.def.id}"
}

/** Grid-mode expanded row: full-width card with the achievement text. */
data class RaGridExpanded(val row: RaAchievementRow) : RaListItem {
    override val key: Any
        get() = "expanded:${row.def.id}"
}

/** View mode for the achievements list: classic rows or badge grid. */
enum class RaViewMode {
    LIST,
    GRID
}

/**
 * List adapter for the achievements screen. Renders two row types from a single flat [RaListItem]
 * list: game section headers and achievement rows. The list is pre-sorted by the producer (see
 * buildSectionedRows) so the adapter stays dumb; DiffUtil keeps animations cheap on refresh. The
 * achievement row binding logic lives only in [AchievementViewHolder] and is never duplicated.
 */
class RaAchievementAdapter : ListAdapter<RaListItem, RecyclerView.ViewHolder>(DIFF) {

    /** Current view mode; switching resets the grid expansion. */
    var viewMode: RaViewMode = RaViewMode.LIST
        set(value) {
            if (field != value) {
                field = value
                expandedKey = null
                rebuild()
            }
        }

    /** Key of the achievement currently expanded in grid mode (null = none). */
    var expandedKey: Any? = null
        private set

    /** Raw (pre-mode) list submitted by the host; the display list is derived. */
    private var rawItems: List<RaListItem> = emptyList()

    override fun submitList(list: List<RaListItem>?) {
        rawItems = list.orEmpty()
        rebuild()
    }

    /** Expands/collapses the grid cell identified by [key] (one at a time). */
    fun toggleExpand(key: Any) {
        expandedKey = if (expandedKey == key) null else key
        rebuild()
    }

    /** Rebuilds the display list from [rawItems] according to [viewMode]. */
    private fun rebuild() {
        val display =
                if (viewMode == RaViewMode.GRID) {
                    rawItems.map { item ->
                        when (item) {
                            is RaSectionItem -> item
                            is RaAchievementRow ->
                                    if (item.key == expandedKey) RaGridExpanded(item)
                                    else RaGridCell(item)
                            // Raw lists never carry grid items, but keep the when exhaustive.
                            is RaGridCell,
                            is RaGridExpanded -> item
                        }
                    }
                } else {
                    rawItems
                }
        super.submitList(display)
    }

    /**
     * Wires the span-size lookup for a [GridLayoutManager]: section headers and expanded rows span
     * the full width, badge cells take a single span.
     */
    fun configureLayoutManager(lm: RecyclerView.LayoutManager) {
        if (lm is GridLayoutManager) {
            lm.spanSizeLookup =
                    object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            val item = getItem(position)
                            return when (item) {
                                is RaSectionItem, is RaGridExpanded -> lm.spanCount
                                else -> 1
                            }
                        }
                    }
        }
    }

    override fun getItemViewType(position: Int): Int =
            when (getItem(position)) {
                is RaSectionItem -> VIEW_TYPE_SECTION
                is RaGridCell -> VIEW_TYPE_GRID_CELL
                is RaAchievementRow, is RaGridExpanded -> VIEW_TYPE_ACHIEVEMENT
            }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION ->
                    SectionViewHolder(ItemRaSectionHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_GRID_CELL ->
                    GridCellViewHolder(
                            ItemRaAchievementGridBinding.inflate(inflater, parent, false),
                            onCellClick = { key -> toggleExpand(key) }
                    )
            else -> AchievementViewHolder(ItemRaAchievementBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RaSectionItem -> (holder as SectionViewHolder).bind(item)
            is RaGridCell -> (holder as GridCellViewHolder).bind(item)
            is RaAchievementRow -> {
                val vh = holder as AchievementViewHolder
                vh.bind(item)
                vh.itemView.setOnClickListener(null)
            }
            is RaGridExpanded -> {
                val vh = holder as AchievementViewHolder
                vh.bind(item.row)
                vh.itemView.setOnClickListener { toggleExpand(item.key) }
            }
        }
    }

    /** Header row: game title + "x/y conquistas · p/q pontos" summary. */
    class SectionViewHolder(private val binding: ItemRaSectionHeaderBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RaSectionItem) {
            binding.raSectionTitle.text = item.title
            binding.raSectionSummary.text =
                    binding.root.context.getString(
                            R.string.ra_progress_summary,
                            item.unlockedCount,
                            item.totalCount,
                            item.earnedPoints,
                            item.totalPoints
                    )
        }
    }

    /** Achievement row: badge, title, description, points, unlock state + missable + rarity. */
    class AchievementViewHolder(private val binding: ItemRaAchievementBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun bind(row: RaAchievementRow) {
            val context = binding.root.context
            binding.raAchievementTitle.text = row.def.title
            binding.raAchievementDescription.text = row.def.description
            binding.raAchievementPoints.text =
                    context.getString(R.string.ra_achievement_points, row.def.points)
            binding.raAchievementUnlockedIcon.visibility =
                    if (row.unlocked) View.VISIBLE else View.GONE
            binding.raAchievementTitle.alpha = if (row.unlocked) 1f else 0.75f

            val badgeUrl = if (row.unlocked) row.def.badgeUrl else row.def.badgeLockedUrl
            binding.raAchievementBadge.load(badgeUrl) { crossfade(true) }

            // Missable badge (type == 1)
            binding.raAchievementMissableBadge.visibility =
                    if (row.def.isMissable) View.VISIBLE else View.GONE

            // Rarity: show when > 0 (0 means unknown / not provided by API)
            val rarity = row.def.rarity
            if (rarity > 0f && rarity <= 100f) {
                binding.raAchievementRarity.visibility = View.VISIBLE
                binding.raAchievementRarity.text = context.getString(R.string.ra_rarity, rarity)
            } else {
                binding.raAchievementRarity.visibility = View.GONE
            }

            // Hide the whole meta row when neither badge nor rarity is shown
            val showMeta = row.def.isMissable || (rarity > 0f && rarity <= 100f)
            binding.raAchievementMetaRow.visibility = if (showMeta) View.VISIBLE else View.GONE
        }
    }

    /** Grid-mode cell: badge thumbnail + unlocked indicator; tap expands the row. */
    class GridCellViewHolder(
            private val binding: ItemRaAchievementGridBinding,
            private val onCellClick: (Any) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cell: RaGridCell) {
            val context = binding.root.context
            val row = cell.row
            val badgeUrl = if (row.unlocked) row.def.badgeUrl else row.def.badgeLockedUrl
            binding.raGridBadge.load(badgeUrl) { crossfade(true) }
            binding.raGridUnlocked.visibility = if (row.unlocked) View.VISIBLE else View.GONE
            binding.root.contentDescription =
                    context.getString(R.string.ra_grid_cell_desc, row.def.title, row.def.points)
            binding.root.setOnClickListener { onCellClick(cell.key) }
        }
    }

    private companion object {
        private const val VIEW_TYPE_SECTION = 0
        private const val VIEW_TYPE_ACHIEVEMENT = 1
        private const val VIEW_TYPE_GRID_CELL = 2

        val DIFF =
                object : DiffUtil.ItemCallback<RaListItem>() {
                    override fun areItemsTheSame(old: RaListItem, new: RaListItem) =
                            old.key == new.key

                    override fun areContentsTheSame(old: RaListItem, new: RaListItem) = old == new
                }
    }
}
