/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.gallery

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders [GalleryItem]s in the Gallery grid. Each card shows a thumbnail
 * (Coil for images, a [MediaMetadataRetriever] frame for videos) plus a type
 * badge. Legacy overlay screenshots retain their extra badge. Tapping a card
 * invokes [onActivate], which the Activity uses to present the
 * view/share/delete actions (kept here as a single callback to avoid
 * duplicating the dialog in the adapter).
 */
class GalleryAdapter(
    private val onActivate: (GalleryItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var items: List<GalleryItem> = emptyList()

    fun submit(list: List<GalleryItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.gallery_item, parent, false)
        return GalleryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun onViewRecycled(holder: GalleryViewHolder) {
        super.onViewRecycled(holder)
        holder.job?.cancel()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope.coroutineContext[Job]?.cancel()
    }

    inner class GalleryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumb: ImageView = view.findViewById(R.id.gallery_thumb)
        private val typeBadge: TextView = view.findViewById(R.id.gallery_type_badge)
        private val overlayBadge: TextView = view.findViewById(R.id.gallery_overlay_badge)
        var job: Job? = null
        private var boundItem: GalleryItem? = null

        init {
            itemView.setOnClickListener {
                /* bindingAdapterPosition may be NO_POSITION while the gallery
                   refreshes on resume. Keep the bound model instead so a card
                   always opens its action menu when it is visibly tappable. */
                boundItem?.let(onActivate)
            }
        }

        fun bind(item: GalleryItem) {
            job?.cancel()
            boundItem = item
            val isVideo = item.type == MediaType.VIDEO
            typeBadge.text = itemView.context.getString(
                if (isVideo) R.string.gallery_record_badge else R.string.gallery_screenshot_badge
            )
            typeBadge.setBackgroundResource(
                if (isVideo) R.drawable.bg_badge_video else R.drawable.bg_badge_teal
            )
            typeBadge.visibility = View.VISIBLE
            if (item.type == MediaType.IMAGE && item.withOverlay) {
                overlayBadge.visibility = View.VISIBLE
                overlayBadge.text = itemView.context.getString(R.string.gallery_overlay_badge)
            } else {
                overlayBadge.visibility = View.GONE
            }
            // Reset to a neutral state while loading.
            thumb.setImageResource(android.R.color.transparent)
            job = scope.launch {
                val bitmap = loadThumbnail(item)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) thumb.setImageBitmap(bitmap)
                    else thumb.load(item.path)
                }
            }
        }

        private suspend fun loadThumbnail(item: GalleryItem): Bitmap? = runCatching {
            if (item.type == MediaType.VIDEO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(item.path.absolutePath)
                    retriever.getFrameAtTime(0)
                } finally {
                    runCatching { retriever.release() }
                }
            } else {
                null
            }
        }.getOrNull()
    }
}
