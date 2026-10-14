package za.callguard.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Talks to the CallGuard backend (backend/app.py). */
class ApiClient(private val settings: Settings) {

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        request("GET", "/health").optBoolean("ok")
    }

    suspend fun lookup(number: String): LookupResult = withContext(Dispatchers.IO) {
        val j = request("GET", "/v1/lookup?number=" + URLEncoder.encode(number, "UTF-8"))
        val spam = j.getJSONObject("spam")
        val biz = j.optJSONObject("business")
        val labels = spam.optJSONArray("labels")
        LookupResult(
            number = j.getString("number"),
            verdict = spam.getString("verdict"),
            score = spam.optInt("score"),
            reports = spam.optInt("reports"),
            notSpam = spam.optInt("not_spam"),
            category = spam.optStringOrNull("top_category"),
            label = if (labels != null && labels.length() > 0) labels.getString(0) else null,
            businessName = biz?.optStringOrNull("name"),
            carrier = j.optStringOrNull("carrier"),
            location = j.optStringOrNull("location"),
            lineType = j.optStringOrNull("line_type"),
            source = "server",
        )
    }

    suspend fun report(number: String, isSpam: Boolean, category: String = "other", label: String? = null) =
        withContext(Dispatchers.IO) {
            request("POST", "/v1/report", JSONObject().apply {
                put("number", number)
                put("is_spam", isSpam)
                put("category", category)
                if (!label.isNullOrBlank()) put("label", label.take(60))
            })
            Unit
        }

    suspend fun topSpam(limit: Int = 1000): List<LookupResult> = withContext(Dispatchers.IO) {
        val arr = request("GET", "/v1/top-spam?limit=$limit").getJSONArray("numbers")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LookupResult(
                number = o.getString("number"),
                verdict = o.getString("verdict"),
                score = o.optInt("score"),
                reports = o.optInt("reports"),
                category = o.optStringOrNull("top_category"),
                label = o.optStringOrNull("label"),
                source = "offline cache",
            )
        }
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        if (!settings.hasServer) throw IOException("No server set up (Settings tab)")
        val conn = URL(settings.serverUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Device-Id", settings.deviceId)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                throw IOException("Server said $code${if (!detail.isNullOrBlank()) ": $detail" else ""}")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).ifBlank { null }
