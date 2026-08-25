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

package br.com.redclaw.zelda64player.capture

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import br.com.redclaw.zelda64player.repositories.Storage
import java.io.FileOutputStream

/**
 * Orchestrates screenshot capture for the in-game menu.
 *
 * Every capture produces **two** PNGs (per project rules): a "clean" variant
 * (game surface only, no on-screen controls) and an "overlay" variant (game
 * surface with the control / HUD overlays composited on top).
 *
 * ## Mechanism
 * The emulated game renders to a [GLRetroView], which is a [SurfaceView]
 * (confirmed: `com.swordfish.libretrodroid.GLRetroView extends GLSurfaceView`).
 * The on-screen controls ([RadialGamePad], [OcarinaHudView], [RaOverlayView])
 * are ordinary Android `View`s layered above that surface — they do **not** live
 * on the game's SurfaceView, so a PixelCopy of the game surface alone yields the
 * clean frame.
 *
 * - **Clean variant**: `PixelCopy.request(SurfaceView, …)` on the game surface
 *   (API 24+). This is the only reliable way to grab the GL-rendered frame.
 * - **Overlay variant**:
 *   - API 26+: `PixelCopy.request(Window, …)` captures the fully composited
 *     window (game surface + every View overlay) in one call — the simplest and
 *     most accurate path.
 *   - API 24–25: fall back to manual compositing — draw the clean bitmap onto a
 *     canvas, then `View.draw(canvas)` each visible overlay View at its
 *     on-screen offset. Because the overlays are normal Views (the RadialGamePad
 *     renders to a Canvas, not its own SurfaceView), this captures them
 *     correctly. (If a future control did render to its own SurfaceView, this
 *     fallback would miss it; API 26+ Window PixelCopy remains correct.)
 *
 * The capture is asynchronous (PixelCopy callbacks); [onResult] reports overall
 * success (true if at least one of the two variants was saved).
 */
object CaptureManager {

    private const val TAG = "CaptureManager"

    /**
     * Capture both variants for [hackId].
     *
     * @param context an Activity context (needed for the API 26+ Window capture).
     * @param hackId id of the running game (used in the output file name).
     * @param gameSurface the game's [SurfaceView] (the [GLRetroView]).
     * @param overlayViews Views to composite for the "with overlay" variant
     *   (gamepad overlay, Ocarina HUD, RA overlay). May be empty.
     * @param onResult invoked on the main thread with true if any variant saved.
     */
    @SuppressLint("MissingPermission")
    fun captureScreenshot(
        context: android.content.Context,
        hackId: String,
        gameSurface: SurfaceView,
        overlayViews: List<View>,
        onResult: (Boolean) -> Unit
    ) {
        val width = gameSurface.width
        val height = gameSurface.height
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "captureScreenshot: game surface not laid out ($width x $height)")
            onResult(false)
            return
        }

        val storage = Storage.getInstance(context)
        val timestamp = System.currentTimeMillis()
        val cleanFile = storage.screenshotFile(hackId, timestamp, false)
        val overlayFile = storage.screenshotFile(hackId, timestamp, true)
        val handler = Handler(Looper.getMainLooper())

        val cleanBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(gameSurface, cleanBitmap, { cleanResult ->
            if (cleanResult != PixelCopy.SUCCESS) {
                Log.w(TAG, "captureScreenshot: clean PixelCopy failed ($cleanResult)")
                onResult(false)
                return@request
            }
            val cleanSaved = savePng(cleanFile, cleanBitmap)
            captureOverlayVariant(
                context, gameSurface, overlayViews, cleanBitmap, overlayFile, handler
            ) { overlaySaved ->
                onResult(cleanSaved || overlaySaved)
            }
        }, handler)
    }

    /** Produce the "with overlay" variant via Window PixelCopy (API 26+) or
     *  manual View compositing (API 24–25). */
    private fun captureOverlayVariant(
        context: android.content.Context,
        gameSurface: SurfaceView,
        overlayViews: List<View>,
        cleanBitmap: Bitmap,
        overlayFile: java.io.File,
        handler: Handler,
        onResult: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = context as? android.app.Activity
            if (activity != null) {
                captureWindow(activity.window, gameSurface, overlayFile, handler, onResult)
                return
            }
        }
        // Fallback: composite overlay Views onto the clean bitmap.
        val composite = Bitmap.createBitmap(
            cleanBitmap.width, cleanBitmap.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(composite)
        canvas.drawBitmap(cleanBitmap, 0f, 0f, null)
        val surfaceLoc = IntArray(2)
        gameSurface.getLocationOnScreen(surfaceLoc)
        for (view in overlayViews) {
            if (view.visibility != View.VISIBLE) continue
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            canvas.save()
            canvas.translate((loc[0] - surfaceLoc[0]).toFloat(), (loc[1] - surfaceLoc[1]).toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        onResult(savePng(overlayFile, composite))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun captureWindow(
        window: Window,
        gameSurface: SurfaceView,
        overlayFile: java.io.File,
        handler: Handler,
        onResult: (Boolean) -> Unit
    ) {
        val width = gameSurface.width
        val height = gameSurface.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, Rect(0, 0, width, height), out, { result ->
            if (result == PixelCopy.SUCCESS) {
                onResult(savePng(overlayFile, out))
            } else {
                Log.w(TAG, "captureOverlayVariant: window PixelCopy failed ($result)")
                onResult(false)
            }
        }, handler)
    }

    private fun savePng(file: java.io.File, bitmap: Bitmap): Boolean = runCatching {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        true
    }.getOrDefault(false).also { ok ->
        if (!ok) Log.w(TAG, "savePng: failed to write ${file.name}")
    }
}
