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

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.views.BadgeType
import br.com.redclaw.zelda64player.views.HackLibraryEntry

/**
 * Binds a [HackLibraryEntry]'s family/type badge onto an [ImageView], keeping the chip background
 * and icon tint consistent with the rest of the app (OoT = yellow chip / black icon, MM = purple
 * chip / white icon, unknown = neutral chip).
 *
 * Extracted from the legacy grid adapter so both the Switch home row and the (Phase C) grid screen
 * render badges identically without duplicating the per-family branching (DRY).
 */
object BadgeBinder {

    /**
     * Applies the badge for [entry] to [badge], or hides it when none applies.
     *
     * Vanilla base ROMs ([BadgeType.VANILLA] or [HackLibraryEntry.isVanilla]) carry no badge by
     * design. Store hacks ([BadgeType.HACK]) delegate to [bindFamily] so the family icon matches
     * the rest of the app.
     */
    fun bind(badge: ImageView, entry: HackLibraryEntry) {
        val type = entry.badge
        // Vanilla base ROMs must have no badge.
        if (type == null || type == BadgeType.VANILLA || entry.isVanilla) {
            badge.visibility = View.GONE
            return
        }
        bindFamily(badge, entry.family)
    }

    /** Resolves the game family from a catalog `supportedGames` string (e.g. "OoT", "MM"). */
    fun familyFromSupportedGames(supportedGames: String?): OcarinaGame? =
            when {
                supportedGames?.contains("OoT", ignoreCase = true) == true -> OcarinaGame.OOT
                supportedGames?.contains("MM", ignoreCase = true) == true -> OcarinaGame.MM
                else -> null
            }

    /** Resolves the game family from a N64 header `gameCode` (e.g. "CZLE" -> OoT, "NSME" -> MM). */
    fun familyFromGameCode(gameCode: String?): OcarinaGame? =
            when {
                gameCode == null -> null
                gameCode.startsWith("CZL", ignoreCase = true) -> OcarinaGame.OOT
                gameCode.startsWith("NZL", ignoreCase = true) ||
                        gameCode.startsWith("NSM", ignoreCase = true) ||
                        gameCode.startsWith("NZS", ignoreCase = true) -> OcarinaGame.MM
                else -> null
            }

    /**
     * Resolves the game family for a [HackEntry], preferring [supportedGames] and falling back to
     * [br.com.redclaw.zelda64player.data.model.BaseRomRef.gameCode]. This guarantees the badge is
     * shown even when the catalog omits `supportedGames` (curated PICKS entries).
     */
    fun familyForHack(hack: br.com.redclaw.zelda64player.data.model.HackEntry): OcarinaGame? =
            familyFromSupportedGames(hack.supportedGames)
                    ?: familyFromGameCode(hack.baseRom.gameCode)

    /**
     * Applies the family icon badge to [imageView]: OoT -> [R.drawable.ic_oot] on the yellow chip
     * (black icon), MM -> [R.drawable.ic_mm] on the purple chip (white icon), and an unknown family
     * falls back to the generic [R.drawable.ic_hack] on a neutral chip. Used by both the Library
     * tiles and the Store (detail + grid).
     */
    fun bindFamily(imageView: ImageView, family: OcarinaGame?) {
        val drawableRes: Int
        val bgRes: Int
        val iconTint: Int
        val contentDescriptionRes: Int
        when (family) {
            OcarinaGame.OOT -> {
                drawableRes = R.drawable.ic_oot
                bgRes = R.drawable.bg_badge_oot
                iconTint = R.color.color_badge_oot_icon
                contentDescriptionRes = R.string.game_oot
            }
            OcarinaGame.MM -> {
                drawableRes = R.drawable.ic_mm
                bgRes = R.drawable.bg_badge_mm
                iconTint = R.color.color_badge_mm_icon
                contentDescriptionRes = R.string.game_mm
            }
            null -> {
                drawableRes = R.drawable.ic_hack
                bgRes = R.drawable.bg_badge
                iconTint = android.R.color.white
                contentDescriptionRes = R.string.hack_badge_content_description
            }
        }

        imageView.visibility = View.VISIBLE
        imageView.setImageResource(drawableRes)
        imageView.setBackgroundResource(bgRes)
        imageView.imageTintList = ColorStateList.valueOf(imageView.context.getColor(iconTint))
        imageView.contentDescription = imageView.context.getString(contentDescriptionRes)
    }
}
