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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import br.com.redclaw.zelda64player.BuildConfig
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.ui.switchui.SwitchDialog
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.databinding.ActivityGalleryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gallery screen (Nintendo Switch style): a grid of captured screenshots and
 * recordings. Each card opens a Switch-style action dialog offering View
 * (native viewer via [FileProvider]), Share ([Intent.ACTION_SEND]) and Delete
 * (confirmation then [GalleryViewModel.delete]). The list is observed from
 * [GalleryViewModel] and refreshed on create / resume.
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var viewModel: GalleryViewModel
    private lateinit var adapter: GalleryAdapter

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    /** FileProvider authority, derived from the application id (matches the
     *  provider declared in AndroidManifest.xml). */
    private val fileProviderAuthority = "${BuildConfig.APPLICATION_ID}.fileprovider"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        viewModel = GalleryViewModel(application)
        adapter = GalleryAdapter(onActivate = { showItemActions(it) })

        binding.galleryRecycler.layoutManager = GridLayoutManager(this, 3)
        binding.galleryRecycler.adapter = adapter

        binding.galleryBack.setOnClickListener {
            sfx?.back()
            finish()
        }

        lifecycleScope.launch {
            viewModel.items.collectLatest { items ->
                adapter.submit(items)
                binding.galleryRecycler.visibility =
                    if (items.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                binding.galleryEmpty.visibility =
                    if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun onBackPressed() {
        sfx?.back()
        super.onBackPressed()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            sfx?.back()
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Present View / Share / Delete actions for [item] in a Switch dialog. */
    private fun showItemActions(item: GalleryItem) {
        val options = listOf(
            getString(R.string.gallery_view),
            getString(R.string.gallery_share),
            getString(R.string.gallery_delete)
        )
        SwitchDialog(this)
            .title(getString(R.string.gallery_title))
            .icon(R.drawable.ic_gallery)
            .singleChoice(options, 0) { index ->
                when (index) {
                    0 -> viewItem(item)
                    1 -> shareItem(item)
                    2 -> confirmDelete(item)
                }
            }
            .show()
    }

    /** Open the item in the system viewer (image/png or video/mp4). */
    private fun viewItem(item: GalleryItem) {
        val uri = FileProvider.getUriForFile(this, fileProviderAuthority, item.path)
        val mime = if (item.type == MediaType.VIDEO) "video/mp4" else "image/png"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.gallery_view)))
    }

    /** Share the item via ACTION_SEND. */
    private fun shareItem(item: GalleryItem) {
        val uri = FileProvider.getUriForFile(this, fileProviderAuthority, item.path)
        val mime = if (item.type == MediaType.VIDEO) "video/mp4" else "image/png"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.gallery_share)))
    }

    /** Confirm then delete the item. */
    private fun confirmDelete(item: GalleryItem) {
        SwitchDialog(this)
            .title(getString(R.string.gallery_delete))
            .message(getString(R.string.gallery_delete_confirm))
            .positiveButton(getString(android.R.string.ok)) { viewModel.delete(item) }
            .negativeButton(getString(android.R.string.cancel))
            .show()
    }
}
