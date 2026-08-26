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

package br.com.redclaw.zelda64player.ui.switchui

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.ui.switchui.AccentManager

/**
 * Reusable right slide-in quick options panel matching the Nintendo Switch HOME
 * menu "Options" drawer: a sharp-edged panel pinned to the end (right) edge,
 * ~50% of the screen wide (minimum 320dp), full height, with a scrim over the
 * content to the left.
 *
 * The component is intentionally dumb: the host supplies a title, an optional
 * header badge icon, and a list of [Row]s (each with its own icon / label /
 * suffix / chevron / focus-variant / click action). D-pad and touch both work;
 * focus traversal moves between rows, the focused row gets a full-width cyan
 * (or amber for theme/appearance actions) border, and the Switch UI sound
 * effects are wired (panelOpen / panelClose on show-dismiss, focusMove on
 * traversal, select on activate). BACK closes the panel (back sound).
 *
 * The panel attaches itself to the host activity's content view and detaches on
 * dismiss, so it leaves no leaked windows behind.
 *
 * @param activity the host [AppCompatActivity]; its content view is the attach
 *   target and its recreation (e.g. after a theme toggle) naturally tears the
 *   panel down with it.
 */
class SwitchSidePanel(private val activity: AppCompatActivity) {

    /** A single selectable row in the panel. */
    data class Row(
        /** Optional leading icon (e.g. sun/moon, speaker, trophy, gear). */
        val iconRes: Int? = null,
        /** Row label (already localized by the host). */
        val label: String,
        /** Optional gray suffix (e.g. on/off state). */
        val suffix: String? = null,
        /** Show a trailing chevron (drill-down rows). */
        val showChevron: Boolean = false,
        /** Use the amber focus border variant (theme / appearance actions). */
        val amberFocus: Boolean = false,
        /** Action invoked on activate (click / D-pad A). */
        val onClick: () -> Unit
    )

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    private var overlay: OverlayFrame? = null
    private var panelContainer: ViewGroup? = null
    private val rowViews = mutableListOf<RowView>()
    private var initialFocusDone = false

    /** True while the panel is attached and visible. */
    val isShowing: Boolean get() = overlay != null

    /**
     * Shows the panel with [title], an optional header [badgeIconRes], and the
     * supplied [rows]. No-op if already showing (call [dismiss] first).
     */
    fun show(title: String, badgeIconRes: Int?, rows: List<Row>) {
        if (isShowing) return
        val context = activity
        val root = OverlayFrame(context)
        LayoutInflater.from(context).inflate(R.layout.switch_side_panel, root, true)
        root.panel = this

        val container = root.findViewById<ViewGroup>(R.id.panel_container)
        val metrics = context.resources.displayMetrics
        val minW = context.resources.getDimensionPixelSize(R.dimen.switch_side_panel_min_width)
        val width = maxOf(minW, (metrics.widthPixels * 0.5f).toInt())
        container.layoutParams = FrameLayout.LayoutParams(
            width,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.END
        )

        // Header.
        val badgeContainer = root.findViewById<View>(R.id.panel_badge)
        badgeContainer.background = AccentManager.createBadgeBackground(activity)
        root.findViewById<ImageView>(R.id.panel_badge_icon).apply {
            if (badgeIconRes != null) {
                setImageResource(badgeIconRes)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
        root.findViewById<TextView>(R.id.panel_title).text = title

        // Rows.
        val rowsHolder = root.findViewById<ViewGroup>(R.id.panel_rows)
        val rowHeight = context.resources.getDimensionPixelSize(
            R.dimen.switch_side_panel_row_height
        )
        rowViews.clear()
        rows.forEachIndexed { index, row ->
            val rowView = RowView(context, row, index == 0)
            rowViews.add(rowView)
            // Explicit fixed height: without it the merged match_parent children
            // stretch a single row across the whole rows container.
            rowsHolder.addView(
                rowView,
                android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    rowHeight
                )
            )
        }

        // Scrim tap closes.
        root.findViewById<View>(R.id.panel_scrim).setOnClickListener { dismiss() }

        // Attach + animate in.
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.addView(root)
        overlay = root
        panelContainer = container

        container.translationX = width.toFloat()
        container.animate()
            .setInterpolator(DecelerateInterpolator())
            .setDuration(ANIM_MS)
            .translationX(0f)
            .start()
        root.findViewById<View>(R.id.panel_scrim).apply {
            alpha = 0f
            animate().setDuration(ANIM_MS).alpha(1f).start()
        }
        sfx?.panelOpen()

        // Focus the first row so D-pad navigation starts there.
        rowViews.firstOrNull()?.requestFocus()
    }

    /** Closes the panel with the slide-out animation, then detaches. */
    fun dismiss() {
        val root = overlay ?: return
        val container = panelContainer ?: run {
            detach(root)
            return
        }
        val width = container.width
        sfx?.panelClose()
        container.animate()
            .setInterpolator(AccelerateInterpolator())
            .setDuration(ANIM_MS)
            .translationX(width.toFloat())
            .withEndAction { detach(root) }
            .start()
        root.findViewById<View>(R.id.panel_scrim).animate()
            .setDuration(ANIM_MS).alpha(0f).start()
        overlay = null
        panelContainer = null
    }

    private fun detach(root: OverlayFrame) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.removeView(root)
        rowViews.clear()
    }

    /**
     * Updates the suffix text of the row at [index] (used for live state rows
     * such as the SFX on/off toggle that stay open after activation).
     */
    fun setRowSuffix(index: Int, suffix: String?) {
        rowViews.getOrNull(index)?.setSuffix(suffix)
    }

    /** Intercepts BACK while the panel is showing so it can close itself. */
    private class OverlayFrame(context: Context) : FrameLayout(context) {
        var panel: SwitchSidePanel? = null

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_BACK
            ) {
                panel?.dismiss()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    /** A focusable, clickable panel row with a full-width focus border. */
    private inner class RowView(
        context: Context,
        private val row: Row,
        private val isFirst: Boolean
    ) : FrameLayout(context) {

        private val border: View
        private val suffixView: TextView

        init {
            LayoutInflater.from(context)
                .inflate(R.layout.switch_side_panel_row, this, true)
            border = findViewById(R.id.row_border)
            // Apply dynamic accent color to focus border
            border.background = if (row.amberFocus) {
                // Amber focus uses the static amber accent
                ContextCompat.getDrawable(context, R.drawable.switch_focus_border_amber)!!
            } else {
                AccentManager.createFocusBorder(context)
            }
            val icon = findViewById<ImageView>(R.id.row_icon)
            if (row.iconRes != null) {
                icon.setImageResource(row.iconRes)
                icon.visibility = View.VISIBLE
            }
            findViewById<TextView>(R.id.row_label).text = row.label
            suffixView = findViewById(R.id.row_suffix)
            setSuffix(row.suffix)
            if (row.showChevron) {
                findViewById<View>(R.id.row_chevron).visibility = View.VISIBLE
            }
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setOnClickListener {
                sfx?.select()
                row.onClick()
            }
        }

        fun setSuffix(text: String?) {
            if (text.isNullOrBlank()) {
                suffixView.visibility = View.GONE
            } else {
                suffixView.text = text
                suffixView.visibility = View.VISIBLE
            }
        }

        override fun onFocusChanged(
            gainFocus: Boolean,
            direction: Int,
            previouslyFocusedRect: Rect?
        ) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            border.visibility = if (gainFocus) View.VISIBLE else View.GONE
            if (gainFocus) {
                if (initialFocusDone) sfx?.focusMove() else initialFocusDone = true
            }
        }
    }

    private companion object {
        const val ANIM_MS = 250L
    }
}
