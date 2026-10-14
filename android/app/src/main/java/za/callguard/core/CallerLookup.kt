package za.callguard.core

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Decides who is calling. Order of checks:
 *   1. your own block list (instant, offline)
 *   2. the server (live community data, with a short timeout so the phone keeps ringing on time)
 *   3. the offline spam cache synced in the background
 * A name you saved for the number is added on top of whichever answer wins.
 */
class CallerLookup(context: Context) {
    private val store = Store.get(context)
    private val settings = Settings(context)
    private val api = ApiClient(settings)

    suspend fun identify(number: String, timeoutMs: Long = 3000): LookupResult {
        val base = baseLookup(number, timeoutMs)
        val mine = store.savedName(number)
        return if (mine != null) base.copy(myName = mine) else base
    }

    private suspend fun baseLookup(number: String, timeoutMs: Long): LookupResult {
        if (store.isBlocked(number)) {
            val cached = store.cached(number)
            return (cached ?: LookupResult.unknown(number)).copy(verdict = "blocked", source = "your block list")
        }
        val fresh = if (!settings.hasServer) null
            else withTimeoutOrNull(timeoutMs) { runCatching { api.lookup(number) }.getOrNull() }
        if (fresh != null) {
            store.putCache(listOf(fresh))
            return fresh
        }
        return store.cached(number) ?: LookupResult.unknown(number)
    }

    /** Numbers you've named yourself are trusted: community spam votes won't auto-block them. */
    fun shouldBlock(result: LookupResult): Boolean =
        result.isBlockedByUser || (settings.autoBlockSpam && result.isSpam && result.myName == null)
}
