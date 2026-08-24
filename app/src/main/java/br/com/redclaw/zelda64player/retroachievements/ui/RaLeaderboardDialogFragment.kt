package br.com.redclaw.zelda64player.retroachievements.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Leaderboard listing for the running game.
 *
 * Per project rules this dialog is reachable ONLY from the GameActivity
 * in-game menu — never overlaid on gameplay outside that menu. Shows the
 * game's leaderboard definitions (title + description) fetched through the
 * standalone rapi endpoint so it works regardless of session state.
 */
class RaLeaderboardDialogFragment : DialogFragment() {

    private val scope = MainScope()
    private lateinit var container: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        this.container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val scroll = android.widget.ScrollView(context).apply {
            addView(this@RaLeaderboardDialogFragment.container)
        }
        this.container.addView(makeHeader(context))
        return scroll
    }

    /** Bold Switch-style section header shown above the leaderboard list. */
    private fun makeHeader(context: android.content.Context): TextView =
        TextView(context).apply {
            setText(R.string.ra_leaderboards_title)
            setTextColor(
                androidx.core.content.ContextCompat.getColor(context, R.color.switch_text_primary)
            )
            textSize = 18f
            paint.isFakeBoldText = true
            val density = resources.displayMetrics.density
            setPadding(0, 0, 0, (12 * density).toInt())
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gameId = arguments?.getLong(ARG_GAME_ID, 0L) ?: 0L
        if (gameId == 0L) {
            addMessage(R.string.ra_error_untracked)
            return
        }
        addMessage(R.string.ra_loading)
        load(gameId)
    }

    private fun load(gameId: Long) {
        val repository = Zelda64PlayerApp.raCatalogRepository
        val credentials = Zelda64PlayerApp.raCredentialStore
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                repository.fetchGameData(
                    gameId,
                    credentials.getUsername().orEmpty(),
                    credentials.getToken().orEmpty()
                )
            }
            if (!isAdded) return@launch
            container.removeAllViews()

            val visible = data?.leaderboards?.filter { !it.hidden }.orEmpty()
            if (visible.isEmpty()) {
                addMessage(R.string.ra_leaderboards_empty)
                return@launch
            }
            for (leaderboard in visible) {
                container.addView(makeRow(leaderboard.title, leaderboard.description))
            }
        }
    }

    /** One leaderboard definition rendered as a flat Switch-style row. */
    private fun makeRow(title: String, description: String): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }
            setBackgroundResource(R.drawable.bg_menu_item)
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        card.addView(TextView(context).apply {
            text = title
            setTextColor(
                androidx.core.content.ContextCompat.getColor(context, R.color.switch_text_primary)
            )
            textSize = 15f
            paint.isFakeBoldText = true
        })
        if (description.isNotBlank()) {
            card.addView(TextView(context).apply {
                text = description
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(context, R.color.switch_text_secondary)
                )
                textSize = 13f
            })
        }
        return card
    }

    private fun addMessage(resId: Int) {
        val context = requireContext()
        container.addView(TextView(context).apply {
            setText(resId)
            setTextColor(
                androidx.core.content.ContextCompat.getColor(context, R.color.switch_text_secondary)
            )
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
        })
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.7).toInt()
        )
        dialog?.window?.setBackgroundDrawableResource(R.drawable.bg_switch_dialog)
    }

    override fun onDestroyView() {
        scope.cancel()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_GAME_ID = "game_id"

        /** Creates the dialog for [gameId]. */
        fun newInstance(gameId: Long): RaLeaderboardDialogFragment =
            RaLeaderboardDialogFragment().apply {
                arguments = Bundle().apply { putLong(ARG_GAME_ID, gameId) }
            }
    }
}
