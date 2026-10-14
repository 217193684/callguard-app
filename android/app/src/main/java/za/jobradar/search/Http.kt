package za.jobradar.search

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 14) JobRadar/1.0"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20000): String =
        request("GET", url, headers, null, timeoutMs)

    fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int = 90000): String =
        request("POST", url, headers, body, timeoutMs)

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?, timeoutMs: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(300)}")
            return text
        } finally {
            c.disconnect()
        }
    }

    fun stripHtml(html: String): String = html
        .replace(Regex("(?i)<br\\s*/?>|</p>|</li>|</h\\d>"), "\n")
        .replace(Regex("(?i)<li[^>]*>"), "• ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&#39;", "'").replace("&quot;", "\"").replace("&rsquo;", "’")
        .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n\\s*\\n+"), "\n\n").trim()
}
