package za.callguard.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import za.callguard.core.CallerLookup
import za.callguard.core.LookupResult
import za.callguard.core.PhoneUtil
import za.callguard.core.Settings
import za.callguard.core.Store

/**
 * Android runs this when a call comes in from a number that is NOT in your contacts.
 * We have about 5 seconds to answer "let it ring" or "reject it".
 */
class CallGuardScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allow())
            return
        }
        val settings = Settings(this)
        val store = Store.get(this)
        val number = PhoneUtil.normalize(details.handle?.schemeSpecificPart, settings.region)

        if (number == null) {   // private / hidden number
            if (settings.blockHidden) {
                respondToCall(details, reject())
                store.logCall("Hidden", "Hidden number", "hidden", "blocked")
            } else {
                respondToCall(details, allow())
                store.logCall("Hidden", "Hidden number", "hidden", "allowed")
            }
            return
        }

        scope.launch {
            val lookup = CallerLookup(applicationContext)
            val result: LookupResult = runCatching { lookup.identify(number) }
                .getOrDefault(LookupResult.unknown(number))
            val block = lookup.shouldBlock(result)

            respondToCall(details, if (block) reject() else allow())

            store.logCall(number, result.title(), result.verdict, if (block) "blocked" else "allowed")
            Notifier.showCaller(applicationContext, result, blocked = block)
        }
    }

    private fun allow() = CallResponse.Builder().build()

    private fun reject() = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipNotification(true)   // no missed-call notification for blocked calls
        .setSkipCallLog(false)       // still appears in the phone's call log
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
