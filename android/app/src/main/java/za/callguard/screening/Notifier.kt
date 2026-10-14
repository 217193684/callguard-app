package za.callguard.screening

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import za.callguard.R
import za.callguard.core.LookupResult
import za.callguard.core.WebSearch
import za.callguard.ui.MainActivity

object Notifier {
    private const val CH_CALLER = "caller_id"
    private const val CH_BLOCKED = "blocked_calls"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_CALLER, "Caller ID", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows who is calling while the phone rings"
                setSound(null, null)   // the ringtone is already playing
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_BLOCKED, "Blocked calls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet note when CallGuard rejects a call"
            }
        )
    }

    fun notificationId(number: String) = number.hashCode()

    fun showCaller(context: Context, r: LookupResult, blocked: Boolean) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        // Tapping the notification opens Lookup for this number, ready to name or search it
        val open = PendingIntent.getActivity(
            context, notificationId(r.number),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NUMBER, r.number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val web = PendingIntent.getActivity(
            context, notificationId(r.number) + 1, WebSearch.intent(r.number),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val unnamed = r.myName == null && r.businessName == null
        val title = if (blocked) "Blocked: ${r.title()}" else r.title()
        val color = when {
            blocked || r.isSpam -> 0xFFC62828.toInt()
            r.verdict == "suspicious" -> 0xFFEF6C00.toInt()
            r.businessName != null -> 0xFF1565C0.toInt()
            else -> 0xFF0B6E4F.toInt()
        }

        val b = NotificationCompat.Builder(context, if (blocked) CH_BLOCKED else CH_CALLER)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(title)
            .setContentText(r.subtitle().ifBlank { "Unknown number · tap to name it" })
            .setColor(color)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(if (blocked) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(if (blocked) 6 * 3600_000L else 30 * 60_000L)

        if (!blocked) {
            b.addAction(0, "Report spam", ReportReceiver.intent(context, r.number, isSpam = true))
            b.addAction(0, "Not spam", ReportReceiver.intent(context, r.number, isSpam = false))
            if (unnamed) b.addAction(0, "Who is this?", web)
        } else if (r.isBlockedByUser.not()) {
            b.addAction(0, "Not spam", ReportReceiver.intent(context, r.number, isSpam = false))
        }
        try {
            nm.notify(notificationId(r.number), b.build())
        } catch (e: SecurityException) {
            // notification permission was revoked; nothing to show
        }
    }
}
