package de.fantasyscout.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.fantasyscout.app.data.HttpBackendRepository
import de.fantasyscout.app.data.ComstatsRepository
import de.fantasyscout.app.data.NotificationHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SyncScheduler {
    private const val WORK_NAME = "fantasyscout-live-sync"
    private const val SETTINGS = "fantasyscout_settings"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<LiveSyncWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class LiveSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = applicationContext.getSharedPreferences("fantasyscout_settings", Context.MODE_PRIVATE)
        try {
            val snapshot = ComstatsRepository().fetchSnapshot()
            val favorites = applicationContext.getSharedPreferences("fantasyscout", Context.MODE_PRIVATE).getStringSet("favorites", emptySet()).orEmpty()
            val seen = applicationContext.getSharedPreferences("fantasyscout_alerts", Context.MODE_PRIVATE).getStringSet("seen", emptySet()).orEmpty().toMutableSet()
            val newSeen = seen.toMutableSet()
            val pushNews = settings.getBoolean("push_news", true)
            val pushInjuries = settings.getBoolean("push_injuries", true)
            for (alert in snapshot.alerts) {
                if (alert.id.isBlank() || seen.contains(alert.id)) continue
                val favorite = (alert.playerId != null && favorites.contains(alert.playerId)) || (alert.playerName != null && favorites.contains(alert.playerName))
                if (!favorite) continue
                val isInjury = alert.type == "high_negative" || alert.type == "watch"
                if ((isInjury && !pushInjuries) || (!isInjury && !pushNews)) continue
                NotificationHelper.show(applicationContext, "FantasyScout · ${alert.type}", alert.title, alert.id.hashCode())
                newSeen += alert.id
            }
            while (newSeen.size > 300) newSeen.remove(newSeen.first())
            applicationContext.getSharedPreferences("fantasyscout_alerts", Context.MODE_PRIVATE).edit().putStringSet("seen", newSeen).apply()
            HttpBackendRepository.saveCache(applicationContext, snapshot)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
