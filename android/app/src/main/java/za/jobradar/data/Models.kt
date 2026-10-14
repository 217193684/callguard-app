package za.jobradar.data

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}

fun List<String>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }

/** One uploaded CV. Text is extracted on the phone; skills/titles are detected locally (free). */
data class Cv(
    val id: String,
    val name: String,
    val text: String,
    val skills: List<String>,
    val titles: List<String>,
    val addedAt: Long,
    val alertOn: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("text", text)
        .put("skills", skills.toJsonArray()).put("titles", titles.toJsonArray())
        .put("addedAt", addedAt).put("alertOn", alertOn)

    companion object {
        fun fromJson(o: JSONObject) = Cv(
            id = o.optString("id"), name = o.optString("name"), text = o.optString("text"),
            skills = o.optJSONArray("skills").strings(), titles = o.optJSONArray("titles").strings(),
            addedAt = o.optLong("addedAt"), alertOn = o.optBoolean("alertOn", true),
        )
    }
}

data class Job(
    val id: String,
    val source: String,
    val title: String,
    val company: String,
    val location: String,
    val description: String,
    val url: String,
    val salary: String,
    val postedAt: Long,
    val contract: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("source", source).put("title", title).put("company", company)
        .put("location", location).put("description", description).put("url", url)
        .put("salary", salary).put("postedAt", postedAt).put("contract", contract)

    companion object {
        fun fromJson(o: JSONObject) = Job(
            id = o.optString("id"), source = o.optString("source"), title = o.optString("title"),
            company = o.optString("company"), location = o.optString("location"),
            description = o.optString("description"), url = o.optString("url"),
            salary = o.optString("salary"), postedAt = o.optLong("postedAt"),
            contract = o.optString("contract"),
        )
    }
}

/** Free, on-phone match between a job and your best CV. */
data class Match(
    val score: Int,
    val cvId: String?,
    val cvName: String,
    val matched: List<String>,
    val missing: List<String>,
)

data class ScoredJob(val job: Job, val match: Match)

/** Result of the Claude "Check my fit" button. Cached so a second tap costs nothing. */
data class FitResult(
    val score: Int,
    val bestCv: String,
    val verdict: String,
    val strengths: List<String>,
    val gaps: List<String>,
    val tips: List<String>,
    val ranking: List<Pair<String, Int>>,
    val tokens: Int,
    val at: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("score", score).put("bestCv", bestCv).put("verdict", verdict)
        .put("strengths", strengths.toJsonArray()).put("gaps", gaps.toJsonArray())
        .put("tips", tips.toJsonArray())
        .put("ranking", JSONArray().also { a -> ranking.forEach { a.put(JSONObject().put("cv", it.first).put("score", it.second)) } })
        .put("tokens", tokens).put("at", at)

    companion object {
        fun fromJson(o: JSONObject): FitResult {
            val r = o.optJSONArray("ranking") ?: JSONArray()
            return FitResult(
                score = o.optInt("score"), bestCv = o.optString("bestCv"), verdict = o.optString("verdict"),
                strengths = o.optJSONArray("strengths").strings(), gaps = o.optJSONArray("gaps").strings(),
                tips = o.optJSONArray("tips").strings(),
                ranking = (0 until r.length()).mapNotNull { i ->
                    r.optJSONObject(i)?.let { it.optString("cv") to it.optInt("score") }
                },
                tokens = o.optInt("tokens"), at = o.optLong("at"),
            )
        }
    }
}

val STATUSES = listOf("Saved", "Applied", "Interview", "Offer", "Rejected")

data class TrackedJob(val job: Job, val status: String, val note: String, val updatedAt: Long) {
    fun toJson(): JSONObject = JSONObject().put("job", job.toJson()).put("status", status)
        .put("note", note).put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = TrackedJob(
            Job.fromJson(o.optJSONObject("job") ?: JSONObject()), o.optString("status", "Saved"),
            o.optString("note"), o.optLong("updatedAt"),
        )
    }
}

/** A saved search that the background checker runs for alerts. */
data class SavedSearch(
    val id: String,
    val name: String,
    val query: String,
    val location: String,
    val country: String,
    val remoteOnly: Boolean,
    val salaryMin: Int,
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("query", query)
        .put("location", location).put("country", country).put("remoteOnly", remoteOnly)
        .put("salaryMin", salaryMin).put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject) = SavedSearch(
            o.optString("id"), o.optString("name"), o.optString("query"), o.optString("location"),
            o.optString("country", "za"), o.optBoolean("remoteOnly"), o.optInt("salaryMin"),
            o.optBoolean("enabled", true),
        )
    }
}

/** A job the background checker found for you. */
data class NewMatch(val jobId: String, val score: Int, val cvName: String, val foundAt: Long, val via: String) {
    fun toJson(): JSONObject = JSONObject().put("jobId", jobId).put("score", score)
        .put("cvName", cvName).put("foundAt", foundAt).put("via", via)

    companion object {
        fun fromJson(o: JSONObject) = NewMatch(
            o.optString("jobId"), o.optInt("score"), o.optString("cvName"), o.optLong("foundAt"), o.optString("via"),
        )
    }
}

data class ChatMsg(val role: String, val text: String, val tokens: Int = 0) {
    fun toJson(): JSONObject = JSONObject().put("role", role).put("text", text).put("tokens", tokens)

    companion object {
        fun fromJson(o: JSONObject) = ChatMsg(o.optString("role"), o.optString("text"), o.optInt("tokens"))
    }
}

data class SearchParams(
    val query: String = "",
    val location: String = "",
    val country: String = "za",
    val cvIds: List<String> = emptyList(),
    val daysOld: Int = 0,
    val salaryMin: Int = 0,
    val jobType: String = "any",
    val sort: String = "relevance",
    val exclude: String = "",
    val distanceKm: Int = 0,
    val remoteOnly: Boolean = false,
    val minMatch: Int = 0,
    val sources: Set<String> = setOf("Adzuna", "Careerjet", "Remotive"),
)
