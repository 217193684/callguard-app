package za.callguard

import android.app.Application
import za.callguard.screening.Notifier
import za.callguard.sync.SpamSyncWorker

class CallGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        SpamSyncWorker.schedule(this)
    }
}
