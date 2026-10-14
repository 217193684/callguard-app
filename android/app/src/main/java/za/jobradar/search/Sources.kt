package za.jobradar.search

import android.util.Base64
import org.json.JSONObject
import za.jobradar.data.Job
import za.jobradar.data.SearchParams
import za.jobradar.data.Settings
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class Country(val code: String, val label: String, val careerjet: String, val currency: String)

val COUNTRIES = listOf(
    Country("za", "South Africa", "en_ZA", "R"),
    Country("gb", "United Kingdom", "en_GB", "£"),
    Country("nz", "New Zealand", "en_NZ", "NZ$"),
    Country("au", "Australia", "en_AU", "A$"),
    Country("ca", "Canada", "en_CA", "C$"),
    Country("nl", "Netherlands", "nl_NL", "€"),
    Country("us", "United States", "en_US", "$"),
)

fun country(code: String) = COUNTRIES.firstOrNull { it.code == code } ?: COUNTRIES[0]

/** Sites without a public API: opened inside the app (no scraping). */
object QuickLinks {
    private fun slug(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun all(query: String, location: String, countryCode: String): List<Pair<String, String>> {
        val q = Http.enc(query)
        val l = Http.enc(location)
        val c = country(countryCode)
        val list = mutableListOf<Pair<String, String>>()
        if (c.code == "za") {
            val pnet = buildString {
                append("https://www.pnet.co.za/jobs")
                if (query.isNotBlank()) append("/").append(slug(query))
                if (location.isNotBlank()) append("/in-").append(slug(location))
            }
            list += "PNet" to pnet
            list += "CareerJunction" to "https://www.careerjunction.co.za/jobs/results?keywords=$q&location=$l"
            list += "Careers24" to "https://www.careers24.com/jobs/kw-${slug(query)}/"
        }
        val indeedHost = when (c.code) {
            "za" -> "za.indeed.com"; "gb" -> "uk.indeed.com"; "nz" -> "nz.indeed.com"; "au" -> "au.indeed.com"
            "ca" -> "ca.indeed.com"; "nl" -> "nl.indeed.com"; else -> "www.indeed.com"
        }
        list += "Indeed" to "https://$indeedHost/jobs?q=$q&l=$l"
        list += "LinkedIn" to "https://www.linkedin.com/jobs/search/?keywords=$q&location=${Http.enc(location.ifBlank { c.label })}"
        list += "Google Jobs" to "https://www.google.com/search?q=${Http.enc("$query jobs $location".trim())}&ibp=htl;jobs"
        if (c.code == "nz") list += "Seek NZ" to "https://www.seek.co.nz/${slug(query)}-jobs"
        if (c.code == "au") list += "Seek AU" to "https://www.seek.com.au/${slug(query)}-jobs"
        return list
    }
}

object Sources {
    @Volatile private var myIp: String? = null

    private fun parseDate(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        return runCatching { Instant.parse(s).toEpochMilli() }
            .recoverCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(s.take(19)).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrDefault(0)
    }

    private fun money(cur: String, min: Double, max: Double, predicted: Boolean): String {
        if (min <= 0 && max <= 0) return ""
        fun f(v: Double) = if (v >= 1000) "$cur${"%.0f".format(v / 1000)}k" else "$cur${"%.0f".format(v)}"
        val range = if (max > min && min > 0) "${f(min)}–${f(max)}" else f(maxOf(min, max))
        return range + (if (predicted) " (est.)" else "")
    }

    /** Adzuna: covers SA, UK, NZ, AU, CA, NL, US. whatOr = any-of words (used for CV searches). */
    fun adzuna(p: SearchParams, page: Int = 1): List<Job> {
        if (Settings.adzunaId.isBlank() || Settings.adzunaKey.isBlank()) throw IllegalStateException("add your Adzuna keys in Settings")
        val c = country(p.country)
        val sb = StringBuilder("https://api.adzuna.com/v1/api/jobs/${c.code}/search/$page?")
        sb.append("app_id=").append(Http.enc(Settings.adzunaId)).append("&app_key=").append(Http.enc(Settings.adzunaKey))
        sb.append("&results_per_page=50&content-type=application/json")
        val q = p.query.trim().let { if (p.remoteOnly && !it.contains("remote", true)) "$it remote".trim() else it }
        if (q.contains(" OR ")) sb.append("&what_or=").append(Http.enc(q.split(" OR ").joinToString(" ") { it.trim() }))
        else if (q.isNotBlank()) sb.append("&what=").append(Http.enc(q))
        if (p.location.isNotBlank()) sb.append("&where=").append(Http.enc(p.location))
        if (p.distanceKm > 0) sb.append("&distance=").append(p.distanceKm)
        if (p.daysOld > 0) sb.append("&max_days_old=").append(p.daysOld)
        if (p.salaryMin > 0) sb.append("&salary_min=").append(p.salaryMin)
        if (p.exclude.isNotBlank()) sb.append("&what_exclude=").append(Http.enc(p.exclude))
        when (p.jobType) {
            "permanent" -> sb.append("&permanent=1")
            "contract" -> sb.append("&contract=1")
            "full_time" -> sb.append("&full_time=1")
            "part_time" -> sb.append("&part_time=1")
        }
        when (p.sort) { "date" -> sb.append("&sort_by=date"); "salary" -> sb.append("&sort_by=salary") }

        val root = JSONObject(Http.get(sb.toString()))
        val arr = root.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Job(
                id = "adzuna:" + o.optString("id"),
                source = "Adzuna",
                title = Http.stripHtml(o.optString("title")),
                company = o.optJSONObject("company")?.optString("display_name").orEmpty(),
                location = o.optJSONObject("location")?.optString("display_name").orEmpty(),
                description = Http.stripHtml(o.optString("description")),
                url = o.optString("redirect_url"),
                salary = money(c.currency, o.optDouble("salary_min", 0.0), o.optDouble("salary_max", 0.0),
                    o.optString("salary_is_predicted") == "1") .let { if (it.isNotEmpty()) "$it p.a." else it },
                postedAt = parseDate(o.optString("created")),
                contract = listOf(o.optString("contract_type"), o.optString("contract_time"))
                    .filter { it.isNotBlank() }.joinToString(" · ").replace('_', ' '),
            )
        }
    }

    /** Careerjet v4 API (needs a free partner API key). */
    fun careerjet(p: SearchParams): List<Job> {
        if (Settings.careerjetKey.isBlank()) throw IllegalStateException("add a Careerjet API key in Settings")
        val ip = myIp ?: runCatching { Http.get("https://api.ipify.org", timeoutMs = 8000).trim() }.getOrDefault("").also { myIp = it }
        val c = country(p.country)
        val q = p.query.replace(" OR ", " ").let { if (p.remoteOnly) "$it remote" else it }
        val sb = StringBuilder("https://search.api.careerjet.net/v4/query?")
        sb.append("locale_code=").append(c.careerjet)
        sb.append("&keywords=").append(Http.enc(q.trim()))
        if (p.location.isNotBlank()) sb.append("&location=").append(Http.enc(p.location))
        sb.append("&page_size=50&fragment_size=600")
        sb.append("&sort=").append(if (p.sort == "date") "date" else if (p.sort == "salary") "salary" else "relevance")
        when (p.jobType) { "permanent" -> sb.append("&contract_type=p"); "contract" -> sb.append("&contract_type=c") }
        sb.append("&user_ip=").append(Http.enc(ip.ifBlank { "127.0.0.1" })).append("&user_agent=").append(Http.enc(Http.UA))
        val auth = "Basic " + Base64.encodeToString((Settings.careerjetKey + ":").toByteArray(), Base64.NO_WRAP)

        val root = JSONObject(Http.get(sb.toString(), mapOf("Authorization" to auth)))
        val arr = root.optJSONArray("jobs") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url")
            Job(
                id = "careerjet:" + url.hashCode().toUInt().toString(16),
                source = "Careerjet",
                title = Http.stripHtml(o.optString("title")),
                company = o.optString("company"),
                location = o.optString("locations").ifBlank { o.optString("location") },
                description = Http.stripHtml(o.optString("description")),
                url = url,
                salary = o.optString("salary"),
                postedAt = parseDate(o.optString("date")),
            )
        }
    }

    /** Remotive: free, remote-only jobs worldwide (good for international/remote track). */
    fun remotive(p: SearchParams): List<Job> {
        val q = p.query.split(" OR ").first().trim()
        val root = JSONObject(Http.get("https://remotive.com/api/remote-jobs?limit=60&search=" + Http.enc(q)))
        val arr = root.optJSONArray("jobs") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Job(
                id = "remotive:" + o.optString("id"),
                source = "Remotive",
                title = o.optString("title"),
                company = o.optString("company_name"),
                location = "Remote · " + o.optString("candidate_required_location"),
                description = Http.stripHtml(o.optString("description")).take(5000),
                url = o.optString("url"),
                salary = o.optString("salary"),
                postedAt = parseDate(o.optString("publication_date").let { if (it.length == 19) it + "Z" else it }),
                contract = o.optString("job_type").replace('_', ' '),
            )
        }
    }

    /** Arbeitnow: free European / remote board. No server-side search, so we filter here. */
    fun arbeitnow(p: SearchParams): List<Job> {
        val words = p.query.lowercase().split(Regex("\\s+OR\\s+|\\s+", RegexOption.IGNORE_CASE)).filter { it.length > 2 }
        val out = mutableListOf<Job>()
        for (page in 1..2) {
            val root = JSONObject(Http.get("https://www.arbeitnow.com/api/job-board-api?page=$page"))
            val arr = root.optJSONArray("data") ?: break
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("title")
                val desc = Http.stripHtml(o.optString("description"))
                val hay = (title + " " + desc.take(2000)).lowercase()
                if (words.isNotEmpty() && words.none { hay.contains(it) }) continue
                if (p.remoteOnly && !o.optBoolean("remote")) continue
                out += Job(
                    id = "arbeitnow:" + o.optString("slug"),
                    source = "Arbeitnow",
                    title = title,
                    company = o.optString("company_name"),
                    location = o.optString("location") + if (o.optBoolean("remote")) " · Remote" else "",
                    description = desc.take(5000),
                    url = o.optString("url"),
                    salary = "",
                    postedAt = o.optLong("created_at") * 1000,
                    contract = o.optJSONArray("job_types")?.optString(0).orEmpty(),
                )
            }
        }
        return out
    }
}
