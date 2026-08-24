package br.com.redclaw.zelda64player.retroachievements.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.redclaw.zelda64player.R
import coil.load
import org.json.JSONObject
import java.util.LinkedHashMap

/**
 * In-game RetroAchievements overlay: achievement unlock popups, challenge
 * indicator badges and progress readouts, drawn above the emulation surface.
 *
 * Mirrors the Auto-Ocarina HUD integration contract: created by GameActivity,
 * attached last (top z-order), fully non-interactive so touches fall through
 * to the controls beneath. All mutations happen on the main thread.
 *
 * Event payloads are the JSON documents marshalled by the native bridge.
 */
class RaOverlayView(context: Context) : FrameLayout(context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Popup slot at the top; one unlock at a time (latest wins). */
    private var unlockPopup: LinearLayout? = null
    private var unlockDismissRunnable: Runnable? = null

    /** Challenge indicator badges keyed by achievement id. */
    private val challengeBadges = LinkedHashMap<Long, ImageView>()

    /** Progress indicator row; null while hidden. */
    private var progressRow: TextView? = null

    /** Transient leaderboard/status message row. */
    private var messageRow: TextView? = null
    private var messageDismissRunnable: Runnable? = null

    private val density = resources.displayMetrics.density

    init {
        // Never intercept touches: let them fall through to the gamepad overlay.
        isClickable = false
        isFocusable = false
    }

    /** Shows the achievement-unlock popup described by [payloadJson]. */
    fun showUnlock(payloadJson: String) {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return
        mainHandler.post {
            dismissUnlockPopup()

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_switch_dialog)
                val pad = dp(12)
                setPadding(pad, pad, pad, pad)
            }

            val badge = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                val url = payload.optString("badge_url").takeIf { it.isNotBlank() }
                    ?: payload.optString("badge_locked_url").takeIf { it.isNotBlank() }
                if (url != null) load(url) else setImageResource(R.drawable.ic_trophy)
            }

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                }
            }
            textColumn.addView(makeText(payload.optString("title"), bold = true))
            val points = payload.optInt("points", 0)
            textColumn.addView(
                makeText(
                    context.getString(R.string.ra_unlocked_points, points),
                    bold = false,
                    sizeSp = 12f
                )
            )

            row.addView(badge)
            row.addView(textColumn)

            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            }

            addView(row)
            unlockPopup = row
            alpha = 0f
            animate().alpha(1f).setDuration(200).start()

            unlockDismissRunnable = Runnable { dismissUnlockPopup() }
            unlockDismissRunnable?.let { mainHandler.postDelayed(it, UNLOCK_POPUP_MS) }
        }
    }

    /** Shows (or refreshes) the challenge indicator badge for an achievement. */
    fun showChallengeIndicator(payloadJson: String) {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return
        mainHandler.post {
            val id = payload.optLong("id", 0L)
            if (id == 0L || challengeBadges.containsKey(id)) return@post
            val badge = ImageView(context).apply {
                layoutParams = LayoutParams(dp(36), dp(36)).apply {
                    marginEnd = dp(6)
                }
                val url = payload.optString("badge_url").takeIf { it.isNotBlank() }
                if (url != null) load(url) else setImageResource(R.drawable.ic_trophy)
                alpha = 0.9f
            }
            addView(badge)
            challengeBadges[id] = badge
            relayoutChallengeRow()
        }
    }

    /** Removes the challenge indicator badge for an achievement. */
    fun hideChallengeIndicator(payloadJson: String) {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return
        mainHandler.post {
            val id = payload.optLong("id", 0L)
            challengeBadges.remove(id)?.let {
                removeView(it)
                relayoutChallengeRow()
            }
        }
    }

    /** Shows or updates the progress readout ([payloadJson].measured_progress). */
    fun updateProgressIndicator(payloadJson: String, visible: Boolean) {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull()
        mainHandler.post {
            if (!visible || payload == null) {
                progressRow?.let { removeView(it) }
                progressRow = null
                return@post
            }
            val text = payload.optString("measured_progress").ifBlank {
                "${(payload.optDouble("measured_percent", 0.0) * 100).toInt()}%"
            }
            val row = progressRow ?: makeMessageRow().also {
                addView(it)
                progressRow = it
            }
            row.text = text
        }
    }

    /** Shows a transient status line (leaderboard started/submitted, errors). */
    fun showMessage(text: String) {
        if (text.isBlank()) return
        mainHandler.post {
            val row = messageRow ?: makeMessageRow().also {
                addView(it)
                messageRow = it
            }
            row.text = text
            messageDismissRunnable?.let(mainHandler::removeCallbacks)
            messageDismissRunnable = Runnable {
                messageRow?.let { removeView(it) }
                messageRow = null
            }
            messageDismissRunnable?.let { mainHandler.postDelayed(it, MESSAGE_MS) }
        }
    }

    /** Clears all transient state (called on session stop / view detach). */
    fun clearAll() {
        mainHandler.post {
            dismissUnlockPopup()
            challengeBadges.values.forEach { removeView(it) }
            challengeBadges.clear()
            progressRow?.let { removeView(it) }
            progressRow = null
            messageRow?.let { removeView(it) }
            messageRow = null
        }
    }

    // ------------------------------------------------------------------ //

    private fun dismissUnlockPopup() {
        unlockDismissRunnable?.let(mainHandler::removeCallbacks)
        unlockDismissRunnable = null
        unlockPopup?.let {
            it.animate().alpha(0f).setDuration(150).withEndAction { removeView(it) }.start()
        }
        unlockPopup = null
    }

    private fun makeText(text: String, bold: Boolean, sizeSp: Float = 14f): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.switch_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            paint.isFakeBoldText = bold
            maxWidth = dp(260)
        }

    private fun makeMessageRow(): TextView = TextView(context).apply {
        setTextColor(ContextCompat.getColor(context, R.color.switch_text_primary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setBackgroundResource(R.drawable.bg_switch_dialog)
        val pad = dp(8)
        setPadding(pad, pad / 2, pad, pad / 2)
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(96)
        }
        maxWidth = dp(300)
    }

    /** Lays challenge badges out as a horizontal strip at the top-left. */
    private fun relayoutChallengeRow() {
        var index = 0
        for (badge in challengeBadges.values) {
            (badge.layoutParams as LayoutParams).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dp(16)
                leftMargin = dp(16 + index * 42)
            }
            badge.layoutParams = badge.layoutParams
            index++
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val UNLOCK_POPUP_MS = 4000L
        const val MESSAGE_MS = 2500L
    }
}
