package za.jobradar.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import za.jobradar.R
import za.jobradar.data.NewMatch
import za.jobradar.data.ScoredJob
import za.jobradar.data.SearchParams
import za.jobradar.data.Settings
import za.jobradar.data.Store
import za.jobradar.search.JobSearch
import za.jobradar.ui.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Runs in the background every N minutes: searches for each CV (that has alerts on) and each
 * saved alert, and notifies you about NEW jobs above your match threshold. Uses no Claude tokens.
 */
class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val found = check()
            Settings.lastAlertRun = System.currentTimeMillis()
            Settings.lastAlertError = ""
            if (found.isNotEmpty()) notify(applicationContext, found)
            Result.success()
        } catch (e: Exception) {
            Settings.lastAlertError = e.message ?: "error"
            Result.retry()
        }
    }

    private suspend fun check(): List<Pair<ScoredJob, String>> {
        val minScore = Settings.alertMinScore
        val sources = buildSet {
            if (Settings.adzunaId.isNotBlank()) add("Adzuna")
            if (Settings.careerjetKey.isNotBlank()) add("Careerjet")
        }.ifEmpty { setOf("Remotive") }

        val runs = mutableListOf<Pair<String, SearchParams>>()
        Store.cvs.toList().filter { it.alertOn && it.titles.isNotEmpty() }.forEach { cv ->
            runs += "CV: ${cv.name}" to SearchParams(
                query = JobSearch.queryForCv(cv), country = Settings.defaultCountry,
                location = Settings.defaultLocation, cvIds = listOf(cv.id), daysOld = 3, sort = "date", sources = sources,
            )
        }
        Store.searches.toList().filter { it.enabled }.forEach { s ->
            runs += "Alert: ${s.name}" to SearchParams(
                query = s.query, location = s.location, country = s.country, remoteOnly = s.remoteOnly,
                salaryMin = s.salaryMin, daysOld = 3, sort = "date",
                sources = if (s.remoteOnly) sources + "Remotive" else sources,
            )
        }

        val fresh = LinkedHashMap<String, Pair<ScoredJob, String>>()
        val firstEver = Settings.lastAlertRun == 0L
        for ((via, p) in runs) {
            val out = runCatching { JobSearch.search(p) }.getOrNull() ?: continue
            val newOnes = out.jobs.filter { !Store.isSeen(it.job.id) }
            Store.markSeen(out.jobs.map { it.job.id })
            // First run only fills the "seen" list, so you don't get 200 notifications at once.
            if (firstEver) continue
            newOnes.filter { it.match.score >= minScore }.forEach { fresh.putIfAbsent(it.job.id, it to via) }
        }

        if (fresh.isNotEmpty()) {
            val now = System.currentTimeMillis()
            fresh.values.forEach { (sj, via) ->
                Store.newMatches.add(0, NewMatch(sj.job.id, sj.match.score, sj.match.cvName, now, via))
            }
            while (Store.newMatches.size > 200) Store.newMatches.removeAt(Store.newMatches.lastIndex)
            Store.saveNew()
        }
        return fresh.values.sortedByDescending { it.first.match.score }
    }

    companion object {
        const val CHANNEL = "job_alerts"
        private const val PERIODIC = "job_alerts_periodic"

        fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(CHANNEL, "New job matches", NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = "Jobs that match your CVs"
                ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            }
        }

        private val netOnly = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            if (!Settings.alertsOn) { wm.cancelUniqueWork(PERIODIC); return }
            val mins = Settings.alertMinutes.coerceAtLeast(15).toLong()
            val req = PeriodicWorkRequestBuilder<AlertWorker>(mins, TimeUnit.MINUTES)
                .setConstraints(netOnly)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<AlertWorker>().setConstraints(netOnly).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("job_alerts_now", ExistingWorkPolicy.REPLACE, req)
        }

        fun notify(ctx: Context, found: List<Pair<ScoredJob, String>>) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = NotificationManagerCompat.from(ctx)
            fun open(jobId: String?): PendingIntent {
                val i = Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (jobId != null) putExtra("job_id", jobId) else putExtra("open", "alerts")
                }
                return PendingIntent.getActivity(ctx, (jobId ?: "alerts").hashCode(), i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            // One notification per top job (max 3), plus a summary if there are more.
            found.take(3).forEach { (sj, _) ->
                val j = sj.job
                val n = NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_job)
                    .setContentTitle("${sj.match.score}% match · ${j.title}")
                    .setContentText(listOf(j.company, j.location, j.salary).filter { it.isNotBlank() }.joinToString(" · "))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        listOf(j.company, j.location, j.salary).filter { it.isNotBlank() }.joinToString(" · ") +
                            "\nBest CV: ${sj.match.cvName}"))
                    .setContentIntent(open(j.id))
                    .setAutoCancel(true)
                    .setGroup("jobs")
                    .build()
                runCatching { nm.notify(j.id.hashCode(), n) }
            }
            if (found.size > 3) {
                val s = NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_job)
                    .setContentTitle("${found.size} new jobs match your CVs")
                    .setContentText("Tap to see them all")
                    .setContentIntent(open(null))
                    .setAutoCancel(true)
                    .setGroup("jobs")
                    .setGroupSummary(true)
                    .build()
                runCatching { nm.notify(4242, s) }
            }
        }
    }
}
