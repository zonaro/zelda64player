package br.com.redclaw.zelda64player.retroachievements.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpResponse
import br.com.redclaw.zelda64player.retroachievements.auth.RaCredentialStore
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import br.com.redclaw.zelda64player.utils.CorePrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Persists actual native awards before HTTP; replays with current credentials and original mode/time. */
class RaAwardOutbox(
    private val context: Context,
    private val credentials: RaCredentialStore,
    private val http: RaHttpClient
) {
    private val journal = RaPendingAwards(credentials::readPendingAwards, credentials::writePendingAwards)
    private val replayLock = Mutex()

    internal fun capture(postData: String?): RaPendingAward? = journal.capture(postData)?.also { schedule() }
    internal fun acknowledge(award: RaPendingAward?, response: RaHttpResponse) {
        if (award != null) journal.acknowledge(award, response)
    }

    /** Retries retained failures only for the newly authenticated account. */
    fun onLogin(username: String) {
        journal.retryRejected(username)
        schedule()
    }

    /** Network-constrained unique work survives process termination and retries with backoff. */
    fun schedule() {
        val work = OneTimeWorkRequestBuilder<RaAwardSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, work)
    }

    /** Returns true when no awards for the current account remain. Other accounts are never submitted. */
    suspend fun flush(): Boolean = replayLock.withLock {
        if (!CorePrefs.getRetroAchievementsEnabled(context) || !credentials.hasCredentials()) return true
        val username = credentials.getUsername().orEmpty()
        val token = credentials.getToken().orEmpty()
        val awards = journal.forUser(username).filterNot { it.rejected }
        if (awards.isEmpty()) return true
        // Live rcheevos owns retries during its session. Replay only after teardown/restart.
        if (RcheevosJni.nativeHasClient()) return false
        for (award in awards) {
            if (credentials.getUsername() != username || credentials.getToken() != token) return false
            val elapsed = ((System.currentTimeMillis() - award.earnedAt).coerceAtLeast(0L) / 1000)
                .coerceAtMost(0xffffffffL)
            val request = RcheevosJni.nativeBuildAwardRequest(award.username, token,
                award.achievementId, award.hardcore, award.hash, elapsed) ?: continue
            val response = http.execute(request[0], request.getOrNull(1))
            if (!journal.acknowledge(award, response)) {
                if (journal.forUser(username).any { it.key == award.key && it.rejected }) {
                    br.com.redclaw.zelda64player.retroachievements.RaNotificationHelper.postSyncFailure(context)
                } else return false
            }
        }
        journal.forUser(username).none { !it.rejected }
    }

    companion object { private const val WORK_NAME = "ra-pending-awards" }
}

/** Background recovery of previously earned awards, without loading a ROM or native game session. */
class RaAwardSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if (Zelda64PlayerApp.raAwardOutbox.flush()) Result.success() else Result.retry()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }
}
