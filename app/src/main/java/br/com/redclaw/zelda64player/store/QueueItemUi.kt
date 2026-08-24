package br.com.redclaw.zelda64player.store

/**
 * Immutable UI snapshot of a single queued/active/finished download, shared
 * between the queue manager, the Store grid badge, the detail sheet and the
 * download-queue screen.
 */
enum class DownloadPhase { QUEUED, DOWNLOADING, PATCHING, SUCCESS, ERROR, CANCELLED }

data class QueueItemUi(
    val hackId: String,
    val name: String,
    val coverImageUrl: String?,
    val phase: DownloadPhase,
    val progressPercent: Int,   // 0..100, meaningful in DOWNLOADING
    val downloaded: Long,
    val total: Long,
    val error: String? = null  // set when phase == ERROR
)
