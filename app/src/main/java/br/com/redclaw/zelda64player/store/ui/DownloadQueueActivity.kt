package br.com.redclaw.zelda64player.store.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ActivityDownloadQueueBinding
import br.com.redclaw.zelda64player.store.DownloadQueueManager
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.ui.switchui.SwitchBackButton

/**
 * Lists every queued / active / finished download for the Hack Store. Items can
 * be cancelled while in flight or removed once finished. The list is driven by
 * [DownloadQueueManager.queue] so it stays in sync with the Store grid badges
 * and the progress notifications.
 */
class DownloadQueueActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDownloadQueueBinding
    private lateinit var adapter: DownloadQueueAdapter

    private val backHelper = SwitchBackButton()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        setSupportActionBar(binding.downloadQueueToolbar)
        supportActionBar?.setTitle(R.string.download_queue_title)
        backHelper.attach(this, binding.downloadQueueBack.root, onBack = { finish() })

        adapter = DownloadQueueAdapter(
            onCancel = { DownloadQueueManager.cancel(it) },
            onRemove = { DownloadQueueManager.dismiss(it) }
        )
        binding.downloadQueueRecycler.layoutManager = LinearLayoutManager(this)
        binding.downloadQueueRecycler.adapter = adapter

        DownloadQueueManager.queue.observe(this) { list ->
            adapter.submitList(list)
            val empty = list.isEmpty()
            binding.downloadQueueEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.downloadQueueRecycler.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.download_queue_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_finished -> {
                DownloadQueueManager.clearFinished()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        backHelper.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }
}
