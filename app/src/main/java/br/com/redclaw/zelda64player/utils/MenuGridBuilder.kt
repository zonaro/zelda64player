package br.com.redclaw.zelda64player.utils

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.gamepad.GamePad

/**
 * A single selectable item in a grid menu (mirrors an in-game menu cell).
 *
 * @param id stable identifier used to activate the item via physical keys.
 * @param labelRes string resource for the cell label.
 * @param iconRes drawable resource for the cell icon.
 * @param activeIconRes icon shown when [isActive] is true (toggles only).
 * @param isToggle whether this item reflects a live on/off state.
 * @param isActive returns the current state for toggle items.
 * @param badgeRes optional physical-controller key badge (e.g. LB, START).
 * @param tintIcon when true (default) the icon is tinted to the menu's
 *   on-surface-variant color; set false for self-colored drawables (e.g. the
 *   Auto-Ocarina icon) that must keep their own palette.
 * @param isEnabled returns whether the cell is currently interactive. Disabled
 *   cells are greyed (alpha 0.38f) and excluded from click / focus navigation,
 *   so a D-pad naturally skips them. Defaults to always enabled; the in-game
 *   menu uses this to grey out RetroAchievements items until the RA session
 *   resolves for the running game.
 * @param action invoked when the cell is tapped or activated by key.
 */
data class MenuActionItem(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val activeIconRes: Int = iconRes,
    val isToggle: Boolean = false,
    val isActive: () -> Boolean = { false },
    @StringRes val badgeRes: Int? = null,
    val tintIcon: Boolean = true,
    val isEnabled: () -> Boolean = { true },
    val action: () -> Unit
)

/** A titled group of [MenuActionItem]s rendered as a header + grid row. */
data class MenuSection(
    @StringRes val titleRes: Int,
    val items: List<MenuActionItem>
)

/** Live references to a toggle cell, used to refresh its icon/background. */
data class MenuToggleEntry(
    val item: MenuActionItem,
    val cell: View,
    val icon: ImageView
)

/** Live references to a non-toggle cell, used to refresh its enabled / greyed
 *  state (e.g. RetroAchievements items whose availability resolves after the
 *  menu is first built). Toggles are excluded to avoid double management. */
data class MenuEnabledEntry(
    val item: MenuActionItem,
    val cell: View,
    val icon: ImageView,
    val label: TextView
)

/**
 * Result of [MenuGridBuilder.build]: the dialog content view plus the live
 * references needed to refresh toggle state and badge visibility.
 */
data class BuiltMenu(
    val view: View,
    val toggleEntries: List<MenuToggleEntry>,
    val badgeViews: List<TextView>,
    val enabledEntries: List<MenuEnabledEntry>
)

/**
 * Builds the shared grid-menu view used by both the in-game menu and the
 * Library per-game context menu. The layout mirrors the in-game menu exactly:
 * a scrollable [android.widget.ScrollView] (dialog_game_menu.xml) whose
 * vertical [LinearLayout] holds a bold header per [MenuSection] followed by
 * its icon+label cells laid out in rows of [colCount] via nested weighted
 * [LinearLayout]s (so every cell measures to an equal width inside the
 * unbounded ScrollView).
 *
 * Physical-controller key badges are shown only when a physical controller is
 * connected, i.e. when [GamePad.shouldShowGamePads] is false.
 */
object MenuGridBuilder {

    /**
     * Build the menu view.
     *
     * @param context used for inflation and resource lookups (must be an
     *   Activity so the physical-controller badge rule can be evaluated).
     * @param sections category sections (headers + items) in display order.
     * @param onItemActivated invoked when a cell is tapped; the caller wires
     *   dismissal / post-action behaviour here, keeping one activation path for
     *   both taps and physical keys.
     */
    fun build(
        context: Context,
        sections: List<MenuSection>,
        onItemActivated: (MenuActionItem) -> Unit
    ): BuiltMenu {
        val colCount = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        val cellMargin = context.resources.getDimensionPixelSize(R.dimen.menu_cell_margin)
        val headerTopMargin = context.resources.getDimensionPixelSize(R.dimen.menu_header_top_margin)
        val headerBottomMargin = context.resources.getDimensionPixelSize(R.dimen.menu_header_bottom_margin)
        val headerTextSizePx = context.resources.getDimension(R.dimen.menu_header_text_size)

        /* Badges show only when a physical controller is connected.
           GamePad.shouldShowGamePads() returns TRUE when the on-screen touch
           pads should be shown (i.e. when NO physical controller is connected),
           so the inverse means a controller IS present. */
        val showBadges = (context as? android.app.Activity)
            ?.let { !GamePad.shouldShowGamePads(it) } ?: false

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_game_menu, null)
        val container = view.findViewById<LinearLayout>(R.id.menu_container)

        val toggleEntries = mutableListOf<MenuToggleEntry>()
        val badgeViews = mutableListOf<TextView>()
        val enabledEntries = mutableListOf<MenuEnabledEntry>()

        for (section in sections) {
            val header = TextView(context).apply {
                setText(section.titleRes)
                setTextColor(ContextCompat.getColor(context, R.color.color_on_surface))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, headerTextSizePx)
                setTypeface(null, Typeface.BOLD)
            }
            container.addView(
                header,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, headerTopMargin, 0, headerBottomMargin)
                }
            )

            val items = section.items
            var index = 0
            while (index < items.size) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                val end = minOf(index + colCount, items.size)
                for (j in index until end) {
                    val item = items[j]
                    val cell = LayoutInflater.from(context)
                        .inflate(R.layout.menu_grid_item, row, false)
                    val icon = cell.findViewById<ImageView>(R.id.menu_item_icon)
                    icon.setImageResource(item.iconRes)
                    if (item.tintIcon) {
                        icon.imageTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(context, R.color.color_on_surface_variant)
                        )
                    }
                    val label = cell.findViewById<TextView>(R.id.menu_item_label)
                    label.setText(item.labelRes)
                    /* Tag the cell with its item id so a physical A/DPAD_CENTER/ENTER
                       press can activate the focused cell via the same action path. */
                    cell.tag = item.id
                    cell.setOnClickListener { onItemActivated(item) }
                    if (item.isToggle) {
                        toggleEntries.add(MenuToggleEntry(item, cell, icon))
                    } else {
                        /* Non-toggle cells are tracked so their enabled / greyed
                           state can be refreshed live (e.g. RetroAchievements). */
                        enabledEntries.add(MenuEnabledEntry(item, cell, icon, label))
                    }
                    item.badgeRes?.let { badgeRes ->
                        val badge = cell.findViewById<TextView>(R.id.menu_item_badge)
                        badge.setText(badgeRes)
                        badge.visibility = if (showBadges) View.VISIBLE else View.GONE
                        badgeViews.add(badge)
                    }
                    row.addView(
                        cell,
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                        }
                    )
                }
                container.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                index = end
            }
        }
        return BuiltMenu(view, toggleEntries, badgeViews, enabledEntries)
    }
}
