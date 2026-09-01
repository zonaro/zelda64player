package br.com.redclaw.zelda64player.store.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.databinding.StoreGridItemBinding
import br.com.redclaw.zelda64player.store.DownloadPhase
import br.com.redclaw.zelda64player.store.DownloadTarget
import br.com.redclaw.zelda64player.ui.switchui.BadgeBinder
import coil.load

/**
 * Grid adapter for the Hack Store. Each cell shows the cover, name, author, version and an
 * install-status badge. Tapping a cell opens the detail sheet.
 */
class StoreAdapter(private val onItemClick: (HackEntry) -> Unit) :
        RecyclerView.Adapter<StoreAdapter.ViewHolder>() {

    private var items: List<StoreItem> = emptyList()

    fun update(items: List<StoreItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
                StoreGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
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

        fun bind(item: StoreItem, position: Int) {
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
                    binding.itemStatus.text =
                            binding.root.context.getString(
                                    R.string.store_status_installed,
                                    status.version
                            )
                    binding.itemStatus.setBackgroundResource(R.drawable.store_badge_installed)
                }
                is StoreStatus.UpdateAvailable -> {
                    binding.itemStatus.setText(R.string.store_status_update)
                    binding.itemStatus.setBackgroundResource(R.drawable.store_badge_update)
                }
            }

            // While a download/patch is in flight for this hack, override the
            // install-status badge with a transient progress badge.
            if (item.downloadPhase != null && item.downloadPhase != DownloadPhase.SUCCESS) {
                val text =
                        when (item.downloadPhase) {
                            DownloadPhase.QUEUED -> R.string.store_status_queued
                            DownloadPhase.DOWNLOADING -> R.string.store_status_downloading
                            DownloadPhase.PATCHING -> R.string.store_status_patching
                            else -> null
                        }
                if (text != null) {
                    binding.itemStatus.setText(text)
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

            // Family icon badge (OoT / MM) overlaid on the cover; never hidden for
            // known gameCode — falls back to gameCode when supportedGames is absent.
            BadgeBinder.bindFamily(binding.itemFamilyBadge, BadgeBinder.familyForHack(hack))

            // WebView/manual-download indicator: visible when the hack requires
            // the embedded browser (ExternalLink) instead of a direct download.
            val needsWebView = hack.downloadTarget is DownloadTarget.ExternalLink
            binding.itemWebviewBadge.visibility = if (needsWebView) View.VISIBLE else View.GONE
            binding.itemWebviewBadge.contentDescription =
                    if (needsWebView) binding.root.context.getString(R.string.webview_badge_desc)
                    else null
        }
    }
}
