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

package br.com.redclaw.zelda64player.drive

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.ui.switchui.SwitchBackButton
import br.com.redclaw.zelda64player.ui.switchui.SwitchDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resolves save conflicts detected by [CloudSyncWorker].
 *
 * Opened from the conflict notification (with [EXTRA_CONFLICT_ID]) or from the
 * Settings "View pending conflicts" button (no id -> shows the full list first).
 * The UI follows the Nintendo Switch style via [SwitchDialog]. The activity
 * itself uses the dialog theme so only the scrim + box are visible.
 */
class ConflictResolveActivity : AppCompatActivity() {

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    private val backHelper = SwitchBackButton()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On-screen Switch-style back button. This activity is dialog-themed and
        // immediately presents a modal SwitchDialog whose scrim covers the button;
        // the dialog's own negative/cancel action already finishes the activity.
        // The helper wiring (hide-on-controller, show-on-touch) is kept consistent
        // with the other Switch screens.
        val backButton = layoutInflater.inflate(R.layout.switch_back_button, null)
        val backSize = resources.getDimensionPixelSize(R.dimen.icon_button_size)
        val backMargin = resources.getDimensionPixelSize(R.dimen.switch_screen_margin)
        val backParams = FrameLayout.LayoutParams(backSize, backSize).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(backMargin, backMargin, 0, 0)
        }
        (window.decorView as ViewGroup).addView(backButton, backParams)
        backHelper.attach(this, backButton, onBack = { finish() })

        val store = ConflictStore(this)
        val id = intent.getStringExtra(EXTRA_CONFLICT_ID)
        val record = if (id != null) store.get(id) else null

        if (record != null) {
            showResolution(store, record)
        } else {
            val all = store.getAll()
            if (all.isEmpty()) {
                Toast.makeText(this, R.string.cloudsync_no_conflicts, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            showList(store, all)
        }
    }

    /** Show the list of all pending conflicts; selecting one opens its resolver. */
    private fun showList(store: ConflictStore, all: List<ConflictRecord>) {
        val labels = all.map {
            getString(R.string.cloudsync_conflict_item, it.gameName, it.fileType)
        }
        SwitchDialog(this)
            .title(getString(R.string.cloudsync_pending_title))
            .singleChoice(labels, 0) { which -> showResolution(store, all[which]) }
            .negativeButton(getString(android.R.string.cancel)) { finish() }
            .show()
    }

    /** Show the three-way resolution choice for a single [record]. */
    private fun showResolution(store: ConflictStore, record: ConflictRecord) {
        val local = record.localMeta
        val cloud = record.cloudMeta
        val preview = getString(
            R.string.cloudsync_conflict_preview,
            formatDateTime(local.lastModified),
            local.size,
            local.crc32,
            formatDateTime(parseRfc3339ToEpochMillis(cloud.driveModifiedTime ?: "")),
            cloud.size,
            cloud.crc32
        )
        val options = listOf(
            getString(R.string.cloudsync_keep_local),
            getString(R.string.cloudsync_keep_cloud),
            getString(R.string.cloudsync_keep_both)
        )
        SwitchDialog(this)
            .title(getString(R.string.cloudsync_conflict_title))
            .message(
                getString(
                    R.string.cloudsync_conflict_message,
                    record.gameName,
                    record.fileType
                ) + "\n\n" + preview
            )
            .singleChoice(options, 0) { which ->
                val choice = when (which) {
                    0 -> ResolutionChoice.LOCAL
                    1 -> ResolutionChoice.CLOUD
                    else -> ResolutionChoice.BOTH
                }
                resolve(store, record, choice)
            }
            .negativeButton(getString(android.R.string.cancel)) { finish() }
            .show()
    }

    /** Perform the chosen resolution off the UI thread, then report + finish. */
    private fun resolve(store: ConflictStore, record: ConflictRecord, choice: ResolutionChoice) {
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = CloudSyncWorker.applyResolution(this@ConflictResolveActivity, record, choice)
            withContext(Dispatchers.Main) {
                if (ok) {
                    Toast.makeText(
                        this@ConflictResolveActivity,
                        R.string.cloudsync_resolved,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@ConflictResolveActivity,
                        R.string.cloudsync_resolve_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (ConflictStore(this@ConflictResolveActivity).count() > 0) {
                    showList(
                        ConflictStore(this@ConflictResolveActivity),
                        ConflictStore(this@ConflictResolveActivity).getAll()
                    )
                } else {
                    finish()
                }
            }
        }
    }

    private fun formatDateTime(epoch: Long): String =
        if (epoch <= 0L) {
            getString(R.string.cloudsync_unknown_time)
        } else {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(epoch))
        }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        backHelper.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        /** Extra key carrying the conflict id when launched from a notification. */
        const val EXTRA_CONFLICT_ID = "conflict_id"
    }
}
