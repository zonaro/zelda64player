package br.com.redclaw.zelda64player.ocarina.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.ocarina.OcarinaNote
import br.com.redclaw.zelda64player.ocarina.OcarinaSong

/**
 * On-screen HUD shown while an Ocarina song is being auto-played. Displays the
 * song name and a horizontal row of note chips that light up as each note is
 * sent to the core.
 *
 * The view is non-interactive (clickable/focusable = false) so it never steals
 * touches from the gamepad overlay beneath it; cancellation is driven globally
 * by any physical input (see GameActivityViewModel). The background mirrors the
 * app's dialog surface (bg_menu_dialog) for visual consistency.
 */
class OcarinaHudView(context: Context) : LinearLayout(context) {

    private val nowPlayingView: TextView
    private val nameView: TextView
    private val chipsRow: LinearLayout
    private var chips: List<Pair<TextView, OcarinaNote>> = emptyList()
    private var activeIndex = -1

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundResource(R.drawable.bg_menu_dialog)
        val pad = (12 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        // Never intercept touches: let them fall through to the gamepad overlay.
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        nowPlayingView = TextView(context).apply {
            setText(R.string.ocarina_now_playing)
            setTextColor(ContextCompat.getColor(context, R.color.color_on_surface_variant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        nameView = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.color_on_surface))
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            val vPad = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, vPad, 0, vPad)
        }
        chipsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        addView(nowPlayingView)
        addView(nameView)
        addView(chipsRow)
        visibility = GONE
    }

    /** Show the HUD for [song], building its note chips. */
    fun show(song: OcarinaSong) {
        nameView.text = song.displayName(context)
        buildChips(song.notes)
        activeIndex = -1
        visibility = VISIBLE
    }

    /** Hide the HUD (e.g. on finish or cancellation). */
    fun hide() {
        visibility = GONE
    }

    /** Highlight the chip at [index] as the currently-playing note. */
    fun setActiveNote(index: Int) {
        if (index !in chips.indices) return
        if (activeIndex in chips.indices) {
            val (prevTv, prevNote) = chips[activeIndex]
            styleChip(prevTv, prevNote, ChipState.IDLE)
        }
        activeIndex = index
        val (tv, note) = chips[index]
        styleChip(tv, note, ChipState.ACTIVE)
    }

    /** Mark the chip at [index] as completed (dimmed). */
    fun markComplete(index: Int) {
        if (index !in chips.indices) return
        val (tv, note) = chips[index]
        styleChip(tv, note, ChipState.COMPLETE)
    }

    private fun buildChips(notes: List<OcarinaNote>) {
        chipsRow.removeAllViews()
        val density = resources.displayMetrics.density
        val margin = (4 * density).toInt()
        chips = notes.map { note ->
            val tv = TextView(context).apply {
                text = note.glyph
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                setTypeface(null, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                val size = (40 * density).toInt()
                layoutParams = LayoutParams(size, size).apply { setMargins(margin, margin, margin, margin) }
            }
            chipsRow.addView(tv)
            tv to note
        }
        chips.forEach { (tv, note) -> styleChip(tv, note, ChipState.IDLE) }
    }

    private enum class ChipState { IDLE, ACTIVE, COMPLETE }

    private fun styleChip(tv: TextView, note: OcarinaNote, state: ChipState) {
        when (state) {
            ChipState.IDLE -> {
                tv.background = chipDrawable(note.chipColor, 0.5f)
                tv.alpha = 1f
                tv.scaleX = 1f
                tv.scaleY = 1f
            }
            ChipState.ACTIVE -> {
                tv.background = chipDrawable(note.chipColor, 1f)
                tv.alpha = 1f
                tv.scaleX = 1.35f
                tv.scaleY = 1.35f
            }
            ChipState.COMPLETE -> {
                tv.background = chipDrawable(note.chipColor, 0.5f)
                tv.alpha = 0.4f
                tv.scaleX = 1f
                tv.scaleY = 1f
            }
        }
    }

    private fun chipDrawable(color: Int, alpha: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val a = (alpha * 255).toInt().coerceIn(0, 255)
            setColor(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)))
        }
    }
}
