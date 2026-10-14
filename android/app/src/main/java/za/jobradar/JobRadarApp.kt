package za.jobradar

import android.app.Application
import za.jobradar.alerts.AlertWorker
import za.jobradar.data.Settings
import za.jobradar.data.Store

class JobRadarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Store.init(this)
        AlertWorker.createChannel(this)
        AlertWorker.schedule(this)
    }
}
