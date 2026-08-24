package br.com.redclaw.zelda64player.retroachievements.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ItemRaAchievementBinding
import br.com.redclaw.zelda64player.databinding.ItemRaSectionHeaderBinding
import br.com.redclaw.zelda64player.retroachievements.data.RaAchievementDef
import coil.load

/**
 * Stable list item rendered by [RaAchievementAdapter]. Two concrete kinds exist:
 * a per-game [RaSectionItem] header and an [RaAchievementRow] for one
 * achievement. The [key] provides DiffUtil a stable identity so section headers
 * and achievement rows are never confused across list updates.
 */
sealed interface RaListItem {
    val key: Any
}

/** Display model: definition + live unlock state. */
data class RaAchievementRow(
    val def: RaAchievementDef,
    val unlocked: Boolean
) : RaListItem {
    override val key: Any get() = "ach:${def.id}"
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
    override val key: Any get() = "section:$gameId"
}

/**
 * List adapter for the achievements screen. Renders two row types from a single
 * flat [RaListItem] list: game section headers and achievement rows. The list is
 * pre-sorted by the producer (see buildSectionedRows) so the adapter stays dumb;
 * DiffUtil keeps animations cheap on refresh. The achievement row binding logic
 * lives only in [AchievementViewHolder] and is never duplicated.
 */
class RaAchievementAdapter : ListAdapter<RaListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is RaSectionItem -> VIEW_TYPE_SECTION
            is RaAchievementRow -> VIEW_TYPE_ACHIEVEMENT
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION -> SectionViewHolder(
                ItemRaSectionHeaderBinding.inflate(inflater, parent, false)
            )
            else -> AchievementViewHolder(
                ItemRaAchievementBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RaSectionItem -> (holder as SectionViewHolder).bind(item)
            is RaAchievementRow -> (holder as AchievementViewHolder).bind(item)
        }
    }

    /** Header row: game title + "x/y conquistas · p/q pontos" summary. */
    class SectionViewHolder(private val binding: ItemRaSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RaSectionItem) {
            binding.raSectionTitle.text = item.title
            binding.raSectionSummary.text = binding.root.context.getString(
                R.string.ra_progress_summary,
                item.unlockedCount,
                item.totalCount,
                item.earnedPoints,
                item.totalPoints
            )
        }
    }

    /** Achievement row: badge, title, description, points, unlock state. */
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
            binding.raAchievementBadge.load(badgeUrl) {
                crossfade(true)
            }
        }
    }

    private companion object {
        private const val VIEW_TYPE_SECTION = 0
        private const val VIEW_TYPE_ACHIEVEMENT = 1

        val DIFF = object : DiffUtil.ItemCallback<RaListItem>() {
            override fun areItemsTheSame(old: RaListItem, new: RaListItem) =
                old.key == new.key

            override fun areContentsTheSame(old: RaListItem, new: RaListItem) =
                old == new
        }
    }
}
