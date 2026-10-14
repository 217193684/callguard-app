package za.callguard.core

import android.content.Context
import java.util.UUID

class Settings(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("callguard", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = p.getString("server_url", "") ?: ""
        set(v) = p.edit().putString("server_url", v.trim().trimEnd('/')).apply()

    /** False until a server address is entered: the app then runs on-phone only. */
    val hasServer: Boolean get() = serverUrl.startsWith("http")

    /** Reject calls the community has marked as spam. */
    var autoBlockSpam: Boolean
        get() = p.getBoolean("auto_block_spam", true)
        set(v) = p.edit().putBoolean("auto_block_spam", v).apply()

    /** Reject calls with no caller ID. */
    var blockHidden: Boolean
        get() = p.getBoolean("block_hidden", false)
        set(v) = p.edit().putBoolean("block_hidden", v).apply()

    /** Country used to read local numbers like 082... */
    var region: String
        get() = p.getString("region", "ZA") ?: "ZA"
        set(v) = p.edit().putString("region", v.uppercase()).apply()

    /** Random anonymous ID so the server can count one vote per phone. Not linked to you. */
    val deviceId: String
        get() = p.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("device_id", it).apply()
        }
}
