package za.jobradar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object Settings {
    private lateinit var prefs: SharedPreferences

    /** Bumps whenever a setting changes so screens refresh. */
    var version by mutableIntStateOf(0)
        private set

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private fun str(key: String, def: String = "") = prefs.getString(key, def) ?: def
    private fun putStr(key: String, v: String) { prefs.edit().putString(key, v.trim()).apply(); version++ }
    private fun putInt(key: String, v: Int) { prefs.edit().putInt(key, v).apply(); version++ }
    private fun putBool(key: String, v: Boolean) { prefs.edit().putBoolean(key, v).apply(); version++ }

    var adzunaId: String get() = str("adzunaId"); set(v) = putStr("adzunaId", v)
    var adzunaKey: String get() = str("adzunaKey"); set(v) = putStr("adzunaKey", v)
    var careerjetKey: String get() = str("careerjetKey"); set(v) = putStr("careerjetKey", v)
    var claudeKey: String get() = str("claudeKey"); set(v) = putStr("claudeKey", v)

    /** Haiku is the cheapest model; Sonnet only if you choose it. */
    var claudeModel: String get() = str("claudeModel", "claude-haiku-5-5"); set(v) = putStr("claudeModel", v)

    var alertsOn: Boolean get() = prefs.getBoolean("alertsOn", true); set(v) = putBool("alertsOn", v)
    var alertMinutes: Int get() = prefs.getInt("alertMinutes", 60); set(v) = putInt("alertMinutes", v)
    var alertMinScore: Int get() = prefs.getInt("alertMinScore", 55); set(v) = putInt("alertMinScore", v)
    var defaultCountry: String get() = str("country", "za"); set(v) = putStr("country", v)
    var defaultLocation: String get() = str("location", ""); set(v) = putStr("location", v)

    var tokensIn: Int get() = prefs.getInt("tokensIn", 0); private set(v) = putInt("tokensIn", v)
    var tokensOut: Int get() = prefs.getInt("tokensOut", 0); private set(v) = putInt("tokensOut", v)
    var claudeCalls: Int get() = prefs.getInt("claudeCalls", 0); private set(v) = putInt("claudeCalls", v)
    var lastAlertRun: Long get() = prefs.getLong("lastAlertRun", 0); set(v) { prefs.edit().putLong("lastAlertRun", v).apply(); version++ }
    var lastAlertError: String get() = str("lastAlertError"); set(v) = putStr("lastAlertError", v)

    @Synchronized
    fun addUsage(input: Int, output: Int) {
        tokensIn += input
        tokensOut += output
        claudeCalls += 1
    }

    fun resetUsage() { tokensIn = 0; tokensOut = 0; claudeCalls = 0 }
}
