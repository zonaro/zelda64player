/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import br.com.redclaw.zelda64player.retroview.RetroView

/**
 * Android Presentation that displays the emulator's GLRetroView on a secondary display.
 *
 * When the device is connected to an external monitor (via HDMI, Miracast, or USB-C DisplayPort),
 * this Presentation renders the game on that display while the primary display shows the menu, HUD
 * overlays, and touch controls.
 *
 * The Presentation is a special Dialog that takes ownership of a Display and renders its own window
 * hierarchy on it. The GLRetroView is moved from the Activity's container to this Presentation's
 * container when shown, and returned to the Activity when dismissed.
 *
 * Lifecycle:
 * - Created by [GameActivity] when a secondary display is detected and user preference allows it
 * - Shows the game via [GLRetroView] moved from the primary container
 * - Dismissed when the display is disconnected, preference changes, or the game ends
 */
class GamePresentation(context: Context, display: Display, private val retroView: RetroView) :
        Presentation(context, display, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    companion object {
        private const val TAG = "GamePresentation"
    }

    private var gameContainer: FrameLayout? = null
    private var isShowing = false
    private var pendingAttach = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "GamePresentation created for display: ${display.displayId} (${display.name})")
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setBackgroundDrawableResource(android.R.color.black)
        }
        setupLayout()
        // If show() was called before onCreate completed, attach now.
        if (pendingAttach) {
            pendingAttach = false
            attachGame()
        }
    }

    override fun onStart() {
        super.onStart()
        isShowing = true
        // Ensure layout exists and view is attached.
        if (gameContainer == null) setupLayout()
        attachGame()
        Log.d(TAG, "GamePresentation started on display ${display.displayId}")
    }

    override fun onStop() {
        isShowing = false
        detachGame()
        super.onStop()
        Log.d(TAG, "GamePresentation stopped")
    }

    override fun dismiss() {
        isShowing = false
        // Detach before super.dismiss() so the view can be reattached to primary immediately.
        detachGame()
        try {
            super.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Error dismissing presentation", e)
        }
        Log.d(TAG, "GamePresentation dismissed")
    }

    /** Set up the layout container for the GLRetroView. */
    private fun setupLayout() {
        if (gameContainer != null) return
        val container =
                FrameLayout(context).apply {
                    layoutParams =
                            ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                            )
                    setBackgroundColor(0xFF000000.toInt())
                    keepScreenOn = true
                }
        gameContainer = container
        setContentView(container)
    }

    /** Attach the GLRetroView to this Presentation's display. */
    fun attachGame() {
        val container = gameContainer
        if (container == null) {
            // onCreate not yet called — defer until it is.
            pendingAttach = true
            return
        }
        val glView = retroView.view
        // Already attached to this container?
        if (glView.parent === container) return
        if (glView.parent != null) {
            (glView.parent as? ViewGroup)?.removeView(glView)
        }
        val params =
                FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        .apply { gravity = Gravity.CENTER }
        container.removeAllViews()
        container.addView(glView, params)
        // Ensure the GLSurfaceView resumes rendering in the new window.
        glView.onResume()
        Log.d(TAG, "GLRetroView attached to Presentation display ${display.displayId}")
    }

    /** Detach the GLRetroView (but don't destroy it — the Activity still owns it). */
    fun detachGame() {
        val container = gameContainer ?: return
        val glView = retroView.view
        if (glView.parent === container) {
            // Pause rendering before detaching to avoid surface issues.
            try {
                glView.onPause()
            } catch (_: Exception) {}
            container.removeView(glView)
            Log.d(TAG, "GLRetroView detached from Presentation display")
        }
    }

    /** Check if this Presentation is currently visible on its display. */
    fun isPresentationShowing(): Boolean = isShowing && isShowing()

    /** Expose the container for testing or direct access. */
    fun getContainer(): FrameLayout? = gameContainer
}
