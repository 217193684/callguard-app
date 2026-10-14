package za.callguard.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a web search for a phone number, to see which company or agency publishes it. */
object WebSearch {
    fun intent(e164: String): Intent {
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(PhoneUtil.searchQuery(e164)))
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun open(context: Context, e164: String) {
        try {
            context.startActivity(intent(e164))
        } catch (e: Exception) {
            // no browser installed; nothing to do
        }
    }
}
