/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import br.com.redclaw.zelda64player.utils.CorePrefs
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages display detection, selection, and hot-plug events for multi-monitor support.
 *
 * When the user's device is connected to an external display (TV, monitor, projector), this router
 * determines which display should show the game and which should show the menu. The default
 * behavior is:
 *
 * - Primary display (built-in screen): Shows menu, HUD, overlays, touch controls
 * - Secondary display (external): Shows the game (GLRetroView)
 *
 * The user can override this in Settings:
 * - "auto" (default): Use secondary display for game if available
 * - "primary": Always use primary display for game (single-display mode)
 * - "secondary": Always use secondary display for game
 * - Specific display ID: Use a specific display
 *
 * This class also handles hot-plug events (display connect/disconnect) and notifies registered
 * listeners so the GameActivity can create or dismiss the GamePresentation accordingly.
 */
class DisplayRouter(private val context: Context) {

    companion object {
        private const val TAG = "DisplayRouter"
    }

    /** Callback interface for display change events. */
    interface DisplayListener {
        /** Called when a secondary display becomes available. */
        fun onSecondaryDisplayAvailable(display: Display)

        /** Called when a secondary display is disconnected. */
        fun onSecondaryDisplayDisconnected(displayId: Int)

        /** Called when the game display target changes. */
        fun onGameDisplayChanged(display: Display?)
    }

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<DisplayListener>()

    private var registered = false
    private var currentGameDisplay: Display? = null

    private val displayListener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    Log.d(TAG, "Display added: $displayId")
                    val display = displayManager.getDisplay(displayId)
                    if (display != null && isSecondaryDisplay(display)) {
                        listeners.forEach { it.onSecondaryDisplayAvailable(display) }
                        recalculateGameDisplay()
                    }
                }

                override fun onDisplayRemoved(displayId: Int) {
                    Log.d(TAG, "Display removed: $displayId")
                    if (currentGameDisplay?.displayId == displayId) {
                        currentGameDisplay = null
                        listeners.forEach { it.onSecondaryDisplayDisconnected(displayId) }
                    }
                }

                override fun onDisplayChanged(displayId: Int) {
                    Log.d(TAG, "Display changed: $displayId")
                    recalculateGameDisplay()
                }
            }

    /** Start monitoring display changes. Call this in GameActivity.onCreate(). */
    fun register() {
        if (registered) return
        displayManager.registerDisplayListener(displayListener, mainHandler)
        registered = true
        recalculateGameDisplay()
        Log.d(TAG, "Display monitoring registered")
    }

    /** Stop monitoring display changes. Call this in GameActivity.onDestroy(). */
    fun unregister() {
        if (!registered) return
        displayManager.unregisterDisplayListener(displayListener)
        registered = false
        Log.d(TAG, "Display monitoring unregistered")
    }

    /** Add a listener for display change events. */
    fun addListener(listener: DisplayListener) {
        listeners.add(listener)
    }

    /** Remove a previously added listener. */
    fun removeListener(listener: DisplayListener) {
        listeners.remove(listener)
    }

    /**
     * Get the display that should show the game, based on user preferences and available displays.
     *
     * @return The target display, or null if no suitable display is found
     * ```
     *         (falls back to single-display mode).
     * ```
     */
    fun getGameDisplay(): Display? {
        val mode = CorePrefs.getDisplayOutput(context)

        return when (mode) {
            CorePrefs.DISPLAY_PRIMARY -> getPrimaryDisplay()
            CorePrefs.DISPLAY_SECONDARY -> getSecondaryDisplay()
            CorePrefs.DISPLAY_AUTO -> getSecondaryDisplay() ?: getPrimaryDisplay()
            else -> {
                // Try to parse as a specific display ID.
                val displayId = mode.toIntOrNull()
                if (displayId != null) {
                    displayManager.getDisplay(displayId)
                } else {
                    getSecondaryDisplay() ?: getPrimaryDisplay()
                }
            }
        }
    }

    /** Check if multi-monitor mode is active (game is on a different display than menu). */
    fun isMultiMonitorActive(): Boolean {
        val mode = CorePrefs.getDisplayOutput(context)
        if (mode == CorePrefs.DISPLAY_PRIMARY) return false
        return getSecondaryDisplay() != null
    }

    /** Get all available displays (excluding virtual/undefined). */
    fun getAvailableDisplays(): List<Display> {
        // Prefer presentation-category displays (external monitors, TVs).
        val presentationDisplays =
                displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).filter {
                    it.state != Display.STATE_UNKNOWN
                }
        if (presentationDisplays.isNotEmpty()) return presentationDisplays
        // Fallback: any non-default display with presentation flag or simply non-default.
        return displayManager.displays
                .filter { display ->
                    display.displayId != Display.DEFAULT_DISPLAY &&
                            display.state != Display.STATE_UNKNOWN &&
                            (display.flags and Display.FLAG_PRESENTATION) != 0
                }
                .ifEmpty {
                    displayManager.displays.filter { display ->
                        display.displayId != Display.DEFAULT_DISPLAY &&
                                display.state != Display.STATE_UNKNOWN
                    }
                }
    }

    /** Get the primary (built-in) display. */
    fun getPrimaryDisplay(): Display? = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    /** Get the first available secondary (external) display. */
    fun getSecondaryDisplay(): Display? {
        // Primary source: presentation category (HDMI, Miracast, DisplayPort, etc.)
        displayManager
                .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.state != Display.STATE_UNKNOWN }
                ?.let {
                    return it
                }
        // Fallback: flag-based detection.
        displayManager.displays
                .firstOrNull { display ->
                    display.displayId != Display.DEFAULT_DISPLAY &&
                            display.state != Display.STATE_UNKNOWN &&
                            (display.flags and Display.FLAG_PRESENTATION) != 0
                }
                ?.let {
                    return it
                }
        // Last resort: any non-default display that is on.
        return displayManager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY && display.state != Display.STATE_UNKNOWN
        }
    }

    /** Check if a display is a secondary (external) display. */
    private fun isSecondaryDisplay(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY) return false
        // If it's in presentation category, it's definitely secondary.
        if ((display.flags and Display.FLAG_PRESENTATION) != 0) return true
        // Otherwise treat any non-default, non-unknown display as secondary (covers emulators).
        return display.state != Display.STATE_UNKNOWN
    }

    /** Recalculate which display should show the game and notify listeners if it changed. */
    private fun recalculateGameDisplay() {
        val newDisplay = getGameDisplay()
        if (newDisplay?.displayId != currentGameDisplay?.displayId) {
            currentGameDisplay = newDisplay
            listeners.forEach { it.onGameDisplayChanged(newDisplay) }
            Log.d(TAG, "Game display changed to: ${newDisplay?.name ?: "null (single-display)"}")
        }
    }
}
