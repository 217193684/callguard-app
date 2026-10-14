package za.jobradar.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import za.jobradar.data.Settings
import za.jobradar.search.Http

val MODELS = listOf(
    "claude-haiku-5-5" to "Haiku (cheapest, recommended)",
    "claude-sonnet-5-5" to "Sonnet (smarter, costs more)",
)

class ClaudeReply(val json: JSONObject, val inputTokens: Int, val outputTokens: Int) {
    val content: JSONArray get() = json.optJSONArray("content") ?: JSONArray()
    val stopReason: String get() = json.optString("stop_reason")
    val text: String get() = (0 until content.length())
        .mapNotNull { content.optJSONObject(it) }
        .filter { it.optString("type") == "text" }
        .joinToString("\n") { it.optString("text") }.trim()
}

/** Minimal client for the Claude Messages API. Only ever called when you tap a Claude button. */
object Claude {
    fun ready() = Settings.claudeKey.isNotBlank()

    suspend fun send(
        system: String,
        messages: JSONArray,
        maxTokens: Int,
        tools: JSONArray? = null,
    ): ClaudeReply = withContext(Dispatchers.IO) {
        check(ready()) { "Add your Claude API key in Settings first" }
        val body = JSONObject()
            .put("model", Settings.claudeModel)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", messages)
        if (tools != null && tools.length() > 0) body.put("tools", tools)

        val raw = try {
            Http.post(
                "https://api.anthropic.com/v1/messages",
                mapOf(
                    "x-api-key" to Settings.claudeKey,
                    "anthropic-version" to "2023-06-01",
                    "content-type" to "application/json",
                ),
                body.toString(),
            )
        } catch (e: Exception) {
            val m = e.message ?: ""
            throw IllegalStateException(
                when {
                    "401" in m -> "Claude rejected the API key — check it in Settings"
                    "429" in m -> "Claude rate limit hit — wait a minute and try again"
                    "credit" in m.lowercase() || "billing" in m.lowercase() -> "Your Anthropic account has no API credit left"
                    "404" in m && "model" in m -> "That model isn't available on your key — pick another in Settings"
                    else -> "Claude error: ${m.take(200)}"
                }
            )
        }
        val json = JSONObject(raw)
        val usage = json.optJSONObject("usage") ?: JSONObject()
        val inT = usage.optInt("input_tokens") + usage.optInt("cache_read_input_tokens") + usage.optInt("cache_creation_input_tokens")
        val outT = usage.optInt("output_tokens")
        Settings.addUsage(inT, outT)
        ClaudeReply(json, inT, outT)
    }

    fun userText(text: String): JSONObject = JSONObject().put("role", "user").put("content", text)

    /** Pulls the first {...} block out of a reply. */
    fun extractJson(text: String): JSONObject? {
        val s = text.indexOf('{')
        val e = text.lastIndexOf('}')
        if (s < 0 || e <= s) return null
        return runCatching { JSONObject(text.substring(s, e + 1)) }.getOrNull()
    }
}
