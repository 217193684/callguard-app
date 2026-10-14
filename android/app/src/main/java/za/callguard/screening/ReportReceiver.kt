package za.callguard.screening

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.callguard.core.ApiClient
import za.callguard.core.Settings
import za.callguard.core.Store

/** Handles the "Report spam" / "Not spam" buttons on the caller-ID notification. */
class ReportReceiver : BroadcastReceiver() {

    companion object {
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_SPAM = "is_spam"

        fun intent(context: Context, number: String, isSpam: Boolean): PendingIntent {
            val i = Intent(context, ReportReceiver::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_SPAM, isSpam)
            return PendingIntent.getBroadcast(
                context, (number + isSpam).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
        val isSpam = intent.getBooleanExtra(EXTRA_SPAM, true)
        val app = context.applicationContext
        NotificationManagerCompat.from(app).cancel(Notifier.notificationId(number))

        val store = Store.get(app)
        if (isSpam) store.block(number, "Reported as spam") else store.unblock(number)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching { ApiClient(Settings(app)).report(number, isSpam) }.isSuccess
            withContext(Dispatchers.Main) {
                val msg = when {
                    isSpam && ok -> "Blocked and reported. Thanks!"
                    isSpam -> "Blocked on this phone (server unreachable, report not sent)"
                    ok -> "Marked as not spam"
                    else -> "Unblocked (server unreachable)"
                }
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
                pending.finish()
            }
        }
    }
}
