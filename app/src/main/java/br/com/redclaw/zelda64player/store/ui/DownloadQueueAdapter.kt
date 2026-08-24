package br.com.redclaw.zelda64player.store.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.DownloadQueueItemBinding
import br.com.redclaw.zelda64player.store.DownloadPhase
import br.com.redclaw.zelda64player.store.QueueItemUi
import coil.load

/**
 * Lists the download + patch queue. Active items (QUEUED/DOWNLOADING/PATCHING)
 * expose a Cancel button; finished items (SUCCESS/ERROR/CANCELLED) expose a
 * Remove button. Progress is shown determinately while DOWNLOADING and
 * indeterminately while PATCHING.
 */
class DownloadQueueAdapter(
    private val onCancel: (String) -> Unit,
    private val onRemove: (String) -> Unit
) : ListAdapter<QueueItemUi, DownloadQueueAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<QueueItemUi>() {
            override fun areItemsTheSame(a: QueueItemUi, b: QueueItemUi): Boolean =
                a.hackId == b.hackId

            override fun areContentsTheSame(a: QueueItemUi, b: QueueItemUi): Boolean = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = DownloadQueueItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: DownloadQueueItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ui: QueueItemUi) {
            val context = binding.root.context
            binding.itemName.text = ui.name

            if (ui.coverImageUrl != null) {
                binding.itemCover.load(ui.coverImageUrl) {
                    placeholder(R.drawable.placeholder_cover)
                    error(R.drawable.placeholder_cover)
                    crossfade(true)
                }
            } else {
                binding.itemCover.setImageResource(R.drawable.placeholder_cover)
            }

            val phaseText = when (ui.phase) {
                DownloadPhase.QUEUED -> context.getString(R.string.store_status_queued)
                DownloadPhase.DOWNLOADING -> context.getString(R.string.store_status_downloading)
                DownloadPhase.PATCHING -> context.getString(R.string.store_status_patching)
                DownloadPhase.SUCCESS ->
                    context.getString(R.string.download_notif_completed, ui.name)
                DownloadPhase.CANCELLED ->
                    context.getString(R.string.download_notif_cancelled, ui.name)
                DownloadPhase.ERROR -> ui.error ?: context.getString(R.string.detail_error_generic)
            }
            binding.itemPhase.text = phaseText

            when (ui.phase) {
                DownloadPhase.DOWNLOADING -> {
                    binding.itemProgress.visibility = android.view.View.VISIBLE
                    binding.itemProgress.isIndeterminate = false
                    binding.itemProgress.progress = ui.progressPercent
                }
                DownloadPhase.PATCHING -> {
                    binding.itemProgress.visibility = android.view.View.VISIBLE
                    binding.itemProgress.isIndeterminate = true
                }
                else -> binding.itemProgress.visibility = android.view.View.GONE
            }

            if (ui.phase == DownloadPhase.ERROR) {
                binding.itemError.text = ui.error
                binding.itemError.visibility = android.view.View.VISIBLE
            } else {
                binding.itemError.visibility = android.view.View.GONE
            }

            val active = ui.phase == DownloadPhase.QUEUED ||
                ui.phase == DownloadPhase.DOWNLOADING ||
                ui.phase == DownloadPhase.PATCHING
            if (active) {
                binding.itemAction.setText(R.string.download_cancel)
                binding.itemAction.setOnClickListener { onCancel(ui.hackId) }
            } else {
                binding.itemAction.setText(R.string.download_remove)
                binding.itemAction.setOnClickListener { onRemove(ui.hackId) }
            }
        }
    }
}
