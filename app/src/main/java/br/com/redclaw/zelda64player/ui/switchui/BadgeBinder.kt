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
 * Binds a [HackLibraryEntry]'s family/type badge onto an [ImageView], keeping the
 * chip background and icon tint consistent with the rest of the app (OoT = yellow
 * chip / black icon, MM = purple chip / white icon, unknown = neutral chip).
 *
 * Extracted from the legacy grid adapter so both the Switch home row and the
 * (Phase C) grid screen render badges identically without duplicating the
 * per-family branching (DRY).
 */
object BadgeBinder {

    /** Applies the badge for [entry] to [badge], or hides it when none exists. */
    fun bind(badge: ImageView, entry: HackLibraryEntry) {
        val type = entry.badge ?: run {
            badge.visibility = View.GONE
            return
        }

        val (drawableRes, contentDescriptionRes) = when (type) {
            BadgeType.VANILLA ->
                R.drawable.ic_vanilla to R.string.vanilla_badge_content_description
            BadgeType.HACK ->
                R.drawable.ic_hack to R.string.hack_badge_content_description
        }

        val (bgRes, iconTint) = when (entry.family) {
            OcarinaGame.OOT ->
                R.drawable.bg_badge_oot to
                    badge.context.getColor(R.color.color_badge_oot_icon)
            OcarinaGame.MM ->
                R.drawable.bg_badge_mm to
                    badge.context.getColor(R.color.color_badge_mm_icon)
            null ->
                R.drawable.bg_badge to
                    badge.context.getColor(android.R.color.white)
        }

        badge.visibility = View.VISIBLE
        badge.setImageResource(drawableRes)
        badge.setBackgroundResource(bgRes)
        badge.imageTintList = ColorStateList.valueOf(iconTint)
        badge.contentDescription = badge.context.getString(contentDescriptionRes)
    }
}
