package br.com.redclaw.zelda64player.store.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.StoreScreenshotItemBinding
import coil.load

/** Horizontal gallery adapter for a hack's remote screenshot URLs. */
class ScreenshotAdapter(private val items: List<String>) :
    RecyclerView.Adapter<ScreenshotAdapter.ViewHolder>() {

    class ViewHolder(val binding: StoreScreenshotItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = StoreScreenshotItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.screenshotImage.load(items[position]) {
            placeholder(R.drawable.placeholder_cover)
            error(R.drawable.placeholder_cover)
            crossfade(true)
        }
    }

    override fun getItemCount(): Int = items.size
}
