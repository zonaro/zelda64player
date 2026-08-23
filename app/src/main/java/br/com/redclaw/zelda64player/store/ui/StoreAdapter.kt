package br.com.redclaw.zelda64player.store.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.databinding.StoreGridItemBinding
import coil.load

/**
 * Grid adapter for the Hack Store. Each cell shows the cover, name, author,
 * version and an install-status badge. Tapping a cell opens the detail sheet.
 */
class StoreAdapter(
    private val onItemClick: (HackEntry) -> Unit
) : RecyclerView.Adapter<StoreAdapter.ViewHolder>() {

    private var items: List<StoreItem> = emptyList()

    fun update(items: List<StoreItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = StoreGridItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: StoreGridItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClick(items[pos].hack)
            }
        }

        fun bind(item: StoreItem) {
            val hack = item.hack
            binding.itemName.text = hack.name
            binding.itemAuthor.text = hack.author
            binding.itemVersion.text =
                binding.root.context.getString(R.string.store_version_format, hack.version)

            when (val status = item.status) {
                is StoreStatus.NotInstalled -> {
                    binding.itemStatus.setText(R.string.store_status_not_installed)
                    binding.itemStatus.setBackgroundResource(R.drawable.store_badge_neutral)
                }
                is StoreStatus.Installed -> {
                    binding.itemStatus.text = binding.root.context.getString(
                        R.string.store_status_installed, status.version
                    )
                    binding.itemStatus.setBackgroundResource(R.drawable.store_badge_installed)
                }
                is StoreStatus.UpdateAvailable -> {
                    binding.itemStatus.setText(R.string.store_status_update)
                    binding.itemStatus.setBackgroundResource(R.drawable.store_badge_update)
                }
            }

            if (hack.coverImageUrl != null) {
                binding.itemCover.load(hack.coverImageUrl) {
                    placeholder(R.drawable.placeholder_cover)
                    error(R.drawable.placeholder_cover)
                    crossfade(true)
                }
            } else {
                binding.itemCover.setImageResource(R.drawable.placeholder_cover)
            }
        }
    }
}
