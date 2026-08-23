package br.com.redclaw.zelda64player.viewmodels

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.gamepad.GamePad
import br.com.redclaw.zelda64player.utils.MenuActionItem
import br.com.redclaw.zelda64player.utils.MenuGridBuilder
import br.com.redclaw.zelda64player.utils.MenuSection
import br.com.redclaw.zelda64player.views.HackLibraryEntry

/**
 * Host contract implemented by the Library screen so [LibraryMenuController]
 * can trigger Android-side operations (launch, SAF pickers, confirmation
 * dialogs, toasts) without holding an Activity reference itself. Keeping the
 * controller free of direct Android UI ownership makes the menu structure and
 * key handling the only state it owns.
 */
interface LibraryMenuHost {
    /**
     * The host [Activity], used for dialog inflation, resource lookups and
     * gamepad detection. The only implementer ([LibraryActivity]) is an
     * Activity, so the contract is typed as such.
     */
    fun context(): Activity

    /** Launch [hackId] the same way tapping its tile does (debounce included). */
    fun launchGame(hackId: String)

    /** Begin exporting [entry]'s saves via the SAF document picker. */
    fun requestExportSaves(entry: HackLibraryEntry)

    /** Begin importing [entry]'s saves via the SAF document picker. */
    fun requestImportSaves(entry: HackLibraryEntry)

    /** Show the uninstall confirmation dialog for [entry]. */
    fun confirmUninstall(entry: HackLibraryEntry)

    /** Show the delete-seed confirmation dialog for a randomizer [entry]. */
    fun confirmDeleteSeed(entry: HackLibraryEntry)

    /** Pin [entry] to the home screen (existing shortcut feature). */
    fun pinShortcut(entry: HackLibraryEntry)

    /** Open the RetroAchievements screen for [entry]. */
    fun openAchievements(entry: HackLibraryEntry)

    /** Show a short localized toast by string resource id. */
    fun showToast(resId: Int)
}

/**
 * Owns the per-game context menu shown from the Library grid (via long-press,
 * the tile's overflow button, or physical SELECT/X/Y). Builds the menu with the
 * shared [MenuGridBuilder] so it matches the in-game menu exactly, and mirrors
 * the in-game physical-key handling semantics.
 *
 * The controller holds only menu state (the open dialog, the built sections and
 * badge references); every Android interaction is delegated to [LibraryMenuHost].
 */
class LibraryMenuController(private val host: LibraryMenuHost) {

    private var menuDialog: AlertDialog? = null
    /** Last-built menu sections, kept so physical-key actions reuse the same
     *  item action lambdas as tapping a cell (DRY: one code path). */
    private var menuSections: List<MenuSection>? = null
    /** Live references to badge TextViews, rebuilt every [openMenu]. */
    private val badgeViews = mutableListOf<android.widget.TextView>()

    /** Shared physical-controller key handler, attached to the menu dialog. */
    private val menuKeyListener = View.OnKeyListener { _, keyCode, event ->
        handleMenuKey(keyCode, event)
    }

    /** True while the context menu dialog is showing. */
    fun isMenuShowing(): Boolean = menuDialog?.isShowing == true

    /**
     * Open the per-game context menu for [entry]. Mirrors the in-game menu's
     * window sizing: the dialog wraps the content when it fits and scrolls
     * internally (via the root ScrollView) when it would exceed 90% of the
     * screen height.
     */
    fun openMenu(entry: HackLibraryEntry) {
        val context = host.context()
        val sections = buildSections(entry)
        menuSections = sections
        val built = MenuGridBuilder.build(context, sections) { item ->
            item.action()
            menuDialog?.dismiss()
        }
        badgeViews.clear()
        badgeViews.addAll(built.badgeViews)

        menuDialog = AlertDialog.Builder(context)
            .setTitle(entry.title)
            .setView(built.view)
            .create()
        val dialog = menuDialog ?: return

        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val maxWidthPx = context.resources.getDimensionPixelSize(R.dimen.dialog_menu_max_width)
        val dialogWidth = minOf((screenWidth * 0.92f).toInt(), maxWidthPx)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        built.view.measure(widthSpec, heightSpec)
        val contentHeight = built.view.measuredHeight

        val verticalInset = context.resources.getDimensionPixelSize(R.dimen.dialog_menu_vertical_inset)
        val maxHeight = (screenHeight * 0.90f).toInt()
        val dialogHeight = minOf(contentHeight + verticalInset, maxHeight)

        dialog.show()
        dialog.window?.setLayout(dialogWidth, dialogHeight)
        dialog.window?.setBackgroundDrawable(
            AppCompatResources.getDrawable(context, R.drawable.bg_menu_dialog)
        )
        dialog.window?.decorView?.setOnKeyListener(menuKeyListener)
    }

    /** Dismiss the context menu if showing. */
    fun dismissMenu() {
        if (menuDialog?.isShowing == true) menuDialog?.dismiss()
    }

    /**
     * Build the menu's category sections and their action items for [entry].
     * Actions are lambdas bound to the host, so no click-handler switch is
     * needed (DRY: one cell layout, one click path).
     */
    private fun buildSections(entry: HackLibraryEntry): List<MenuSection> {
        val game = MenuSection(
            R.string.menu_category_game,
            listOf(
                MenuActionItem(
                    "start", R.string.menu_start, R.drawable.ic_play,
                    badgeRes = R.string.badge_start
                ) { host.launchGame(entry.id) },
                MenuActionItem(
                    "achievements", R.string.menu_achievements, R.drawable.ic_trophy
                ) { host.openAchievements(entry) },
                MenuActionItem(
                    "pin", R.string.menu_pin_shortcut, R.drawable.ic_pin
                ) { host.pinShortcut(entry) }
            )
        )
        val saves = MenuSection(
            R.string.menu_category_saves,
            listOf(
                MenuActionItem(
                    "export", R.string.menu_export_saves, R.drawable.ic_export,
                    badgeRes = R.string.badge_rb
                ) { host.requestExportSaves(entry) },
                MenuActionItem(
                    "import", R.string.menu_import_saves, R.drawable.ic_import,
                    badgeRes = R.string.badge_lb
                ) { host.requestImportSaves(entry) }
            )
        )
        val management = MenuSection(
            R.string.menu_category_management,
            if (entry.isRandomizer) {
                listOf(
                    MenuActionItem(
                        "delete_seed", R.string.menu_delete_seed, R.drawable.ic_uninstall,
                        badgeRes = R.string.badge_y
                    ) { host.confirmDeleteSeed(entry) }
                )
            } else {
                listOf(
                    MenuActionItem(
                        "uninstall", R.string.menu_uninstall, R.drawable.ic_uninstall,
                        badgeRes = R.string.badge_y
                    ) { host.confirmUninstall(entry) }
                )
            }
        )
        return listOf(game, saves, management)
    }

    /**
     * Run a menu item's action by id, reusing the exact lambda bound in
     * [buildSections] (the same path a cell tap takes). Mirrors the in-game
     * menu's physical-key activation.
     */
    private fun runMenuAction(id: String) {
        val sections = menuSections ?: return
        for (section in sections) {
            section.items.firstOrNull { it.id == id }?.let { item ->
                item.action()
                menuDialog?.dismiss()
                return
            }
        }
    }

    /**
     * Physical-controller key handler for the context menu, mirroring the
     * in-game [br.com.redclaw.zelda64player.viewmodels.GameActivityViewModel]
     * semantics: act on ACTION_DOWN, consume ACTION_UP (and any other action) so
     * nothing leaks to the grid; B / MODE dismiss; START/RB/LB/Y map to the
     * Start/Export/Import/Uninstall items; A/DPAD_CENTER/ENTER activate the
     * focused cell via its tag. D-pad is left unmapped so focus navigation
     * between cells still works.
     */
    private fun handleMenuKey(keyCode: Int, event: KeyEvent): Boolean {
        if (menuDialog?.isShowing != true) return false

        when (event.action) {
            KeyEvent.ACTION_UP -> return true
            KeyEvent.ACTION_DOWN -> { /* proceed */ }
            else -> return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_MODE -> { dismissMenu(); return true }
            KeyEvent.KEYCODE_BUTTON_B -> { dismissMenu(); return true }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_START -> { runMenuAction("start"); return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { runMenuAction("export"); return true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { runMenuAction("import"); return true }
            KeyEvent.KEYCODE_BUTTON_Y -> { runMenuAction("uninstall"); return true }
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                val tag = menuDialog?.window?.currentFocus?.tag as? String
                if (tag != null) {
                    runMenuAction(tag)
                    return true
                }
                return false
            }
            else -> return false
        }
    }

    /**
     * Show/hide the physical-controller key badges live as controllers connect
     * or disconnect, without rebuilding the menu. Safe to call when the menu is
     * not showing (the badge list is then empty).
     */
    fun refreshBadges() {
        val context = host.context()
        val show = (context as? android.app.Activity)
            ?.let { !GamePad.shouldShowGamePads(it) } ?: false
        for (badge in badgeViews) {
            badge.visibility = if (show) View.VISIBLE else View.GONE
        }
    }
}
