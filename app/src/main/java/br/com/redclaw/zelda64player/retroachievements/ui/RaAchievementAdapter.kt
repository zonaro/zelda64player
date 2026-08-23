package br.com.redclaw.zelda64player.retroachievements.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ItemRaAchievementBinding
import br.com.redclaw.zelda64player.retroachievements.data.RaAchievementDef
import coil.load

/** Display model: definition + live unlock state. */
data class RaAchievementRow(
    val def: RaAchievementDef,
    val unlocked: Boolean
)

/**
 * Flat list adapter for the achievements screen. Rows are pre-sorted by the
 * producer (unlocked first, then locked, both alphabetical) so the adapter
 * stays dumb; DiffUtil keeps animations cheap on refresh.
 */
class RaAchievementAdapter : ListAdapter<RaAchievementRow, RaAchievementAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRaAchievementBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemRaAchievementBinding) :
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
        val DIFF = object : DiffUtil.ItemCallback<RaAchievementRow>() {
            override fun areItemsTheSame(old: RaAchievementRow, new: RaAchievementRow) =
                old.def.id == new.def.id

            override fun areContentsTheSame(old: RaAchievementRow, new: RaAchievementRow) =
                old == new
        }
    }
}
