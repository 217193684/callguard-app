package za.callguard.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import za.callguard.core.ApiClient
import za.callguard.core.Settings
import za.callguard.core.Store
import java.util.concurrent.TimeUnit

/** Downloads the community spam list twice a day so blocking still works without signal. */
class SpamSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val list = ApiClient(Settings(applicationContext)).topSpam()
        Store.get(applicationContext).putCache(list)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SpamSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(online).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("spam-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<SpamSyncWorker>().setConstraints(online).build()
            )
        }
    }
}
