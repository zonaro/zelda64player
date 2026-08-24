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

package br.com.redclaw.zelda64player.views

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ActivitySplashBinding
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive

/**
 * Cold-start splash screen. The Zelda-themed artwork is supplied as the window
 * background by [R.style.Theme_Zelda64Player_Splash] (which references
 * `@drawable/splash_artwork`), so this activity only overlays the crisp
 * wordmark TextViews and drives the timed hand-off to [LibraryActivity].
 *
 * Behavior:
 *  - Holds for [HOLD_MS] (cancellable) and then fades into [LibraryActivity].
 *  - Any tap skips the wait and proceeds immediately.
 *  - BACK is ignored so the user cannot drop into an empty task stack; the
 *    activity simply finishes without navigating.
 *
 * The activity is intentionally theme-light: it performs no emulation, network
 * or disk work, so it is safe to show before [LibraryActivity] rebuilds the
 * library index.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    /** Cancellable timer driving the automatic transition. */
    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable { navigateToLibrary() }

    /** Guards against double navigation (timer + tap + config change). */
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        // Tap anywhere to skip the hold.
        binding.root.setOnClickListener { navigateToLibrary() }

        handler.postDelayed(navigateRunnable, HOLD_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Do not navigate to the Library on BACK; just dismiss the splash so we
        // return to the launcher instead of stranding an empty back stack.
        finish()
    }

    /** Proceeds to the Library exactly once, with a short cross-fade. */
    private fun navigateToLibrary() {
        if (navigated) return
        navigated = true
        handler.removeCallbacks(navigateRunnable)

        val intent = Intent(this, LibraryActivity::class.java)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.fade_in,
            R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
        finish()
    }

    private companion object {
        /** Minimum time the splash stays up before auto-advancing. */
        const val HOLD_MS = 1400L
    }
}
