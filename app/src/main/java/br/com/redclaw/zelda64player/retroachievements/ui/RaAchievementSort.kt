package br.com.redclaw.zelda64player.retroachievements.ui

/**
 * Sort modes for the achievements list. Display order matches the SwitchDialog single-choice list;
 * [DEFAULT] reproduces the original behaviour (unlocked first, then alphabetical).
 */
enum class RaSortMode {
    DEFAULT,
    NAME_AZ,
    NAME_ZA,
    POINTS_DESC,
    POINTS_ASC,
    RARITY_RARE_FIRST,
    RARITY_COMMON_FIRST,
}

/** Sorts a flat list of achievement rows according to [mode]. */
fun sortAchievementRows(rows: List<RaAchievementRow>, mode: RaSortMode): List<RaAchievementRow> =
        when (mode) {
            RaSortMode.DEFAULT ->
                    rows.sortedWith(
                            compareByDescending<RaAchievementRow> { it.unlocked }.thenBy {
                                it.def.title.lowercase()
                            }
                    )
            RaSortMode.NAME_AZ -> rows.sortedBy { it.def.title.lowercase() }
            RaSortMode.NAME_ZA -> rows.sortedByDescending { it.def.title.lowercase() }
            RaSortMode.POINTS_DESC -> rows.sortedByDescending { it.def.points }
            RaSortMode.POINTS_ASC -> rows.sortedBy { it.def.points }
            RaSortMode.RARITY_RARE_FIRST -> rows.sortedBy { it.def.rarity }
            RaSortMode.RARITY_COMMON_FIRST -> rows.sortedByDescending { it.def.rarity }
        }

/** Returns true when [row] matches [query] (title or description, case-insensitive). */
fun matchesQuery(row: RaAchievementRow, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return row.def.title.lowercase().contains(q) || row.def.description.lowercase().contains(q)
}

/**
 * Filters [rows] by [query] and sorts the result by [mode]. Empty query returns all rows sorted.
 */
fun filterAndSortRows(
        rows: List<RaAchievementRow>,
        query: String,
        mode: RaSortMode,
): List<RaAchievementRow> {
    val filtered = if (query.isBlank()) rows else rows.filter { matchesQuery(it, query) }
    return sortAchievementRows(filtered, mode)
}

/**
 * Builds a sectioned [RaListItem] list from [games], applying [query] filter and [mode] sort inside
 * each game's section. Games with zero matching achievements are omitted (no empty section
 * headers).
 */
fun buildFilteredSectionedRows(
        games: List<GameAchievements>,
        query: String,
        mode: RaSortMode,
): List<RaListItem> {
    val rows = mutableListOf<RaListItem>()
    val q = query.trim()
    for (game in games) {
        val achievements = game.gameData.coreAchievements
        val allRows = achievements.map { RaAchievementRow(it, it.id in game.unlockedIds) }
        val filtered = if (q.isBlank()) allRows else allRows.filter { matchesQuery(it, q) }
        if (filtered.isEmpty()) continue

        val sorted = sortAchievementRows(filtered, mode)

        // Recompute section summary from filtered set so counts reflect the filter.
        val unlockedCount = filtered.count { it.unlocked }
        val totalCount = filtered.size
        val earnedPoints = filtered.filter { it.unlocked }.sumOf { it.def.points }
        val totalPoints = filtered.sumOf { it.def.points }

        rows +=
                RaSectionItem(
                        gameId = game.gameId,
                        title = game.title.ifBlank { game.gameData.title },
                        unlockedCount = unlockedCount,
                        totalCount = totalCount,
                        earnedPoints = earnedPoints,
                        totalPoints = totalPoints,
                )
        rows += sorted
    }
    return rows
}
