package za.jobradar.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import za.jobradar.alerts.AlertWorker
import za.jobradar.cv.CvParser
import za.jobradar.data.STATUSES
import za.jobradar.data.SavedSearch
import za.jobradar.data.SearchParams
import za.jobradar.data.Settings
import za.jobradar.data.Store
import za.jobradar.data.strings
import za.jobradar.search.COUNTRIES
import za.jobradar.search.QuickLinks
import za.jobradar.ui.Nav
import za.jobradar.ui.Screen
import za.jobradar.ui.SearchState
import java.util.UUID

/**
 * In-app Claude that can operate the app through tools: search, read jobs, check fit,
 * track applications, manage alerts/CVs/settings and move between screens.
 * Token-saving: short system prompt, only the last few chat turns are sent,
 * tool results are compact, and at most 6 tool rounds per request.
 */
object Assistant {
    private const val MAX_ROUNDS = 6

    private fun tool(name: String, desc: String, props: JSONObject = JSONObject(), required: List<String> = emptyList()) =
        JSONObject().put("name", name).put("description", desc).put(
            "input_schema",
            JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required)),
        )

    private fun p(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)
    private fun arr(desc: String) = JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", desc)

    private val tools: JSONArray by lazy {
        JSONArray()
            .put(tool("search_jobs", "Search job boards and show results on screen. Returns top results with ids and local match %.",
                JSONObject()
                    .put("query", p("string", "keywords/job title; leave empty to search by CVs"))
                    .put("location", p("string", "city/region"))
                    .put("country", p("string", "za, gb, nz, au, ca, nl or us"))
                    .put("cv_names", arr("search using these CVs' job titles"))
                    .put("remote_only", p("boolean", "remote jobs only"))
                    .put("days_old", p("integer", "posted within N days"))
                    .put("salary_min", p("integer", "minimum annual salary"))
                    .put("job_type", p("string", "any, permanent, contract, full_time, part_time"))
                    .put("sort", p("string", "relevance, date, salary or match"))))
            .put(tool("get_job", "Get full details of a job by id.", JSONObject().put("job_id", p("string", "")), listOf("job_id")))
            .put(tool("check_fit", "Deep fit analysis of a job against the user's CVs (uses extra tokens; prefer local match % first).",
                JSONObject().put("job_id", p("string", "")), listOf("job_id")))
            .put(tool("list_cvs", "List uploaded CVs with skills and search titles."))
            .put(tool("update_cv", "Change a CV's search titles, rename it, or turn its job alerts on/off.",
                JSONObject().put("cv_name", p("string", "")).put("titles", arr("job titles used to search for this CV"))
                    .put("new_name", p("string", "")).put("alerts_on", p("boolean", "")), listOf("cv_name")))
            .put(tool("delete_cv", "Delete a CV.", JSONObject().put("cv_name", p("string", "")), listOf("cv_name")))
            .put(tool("track_job", "Save a job to My Jobs or change its status (${STATUSES.joinToString()}), with optional note.",
                JSONObject().put("job_id", p("string", "")).put("status", p("string", "")).put("note", p("string", "")),
                listOf("job_id", "status")))
            .put(tool("untrack_job", "Remove a job from My Jobs.", JSONObject().put("job_id", p("string", "")), listOf("job_id")))
            .put(tool("list_my_jobs", "List saved/applied jobs, optionally by status.", JSONObject().put("status", p("string", ""))))
            .put(tool("create_alert", "Create a saved search that sends notifications for new matching jobs.",
                JSONObject().put("name", p("string", "")).put("query", p("string", "")).put("location", p("string", ""))
                    .put("country", p("string", "")).put("remote_only", p("boolean", "")).put("salary_min", p("integer", "")),
                listOf("query")))
            .put(tool("delete_alert", "Delete a saved alert by name.", JSONObject().put("name", p("string", "")), listOf("name")))
            .put(tool("list_alerts", "List saved alerts and recent new matches found in the background."))
            .put(tool("run_alerts_now", "Check all alerts for new jobs right now (background)."))
            .put(tool("update_settings", "Change app settings.",
                JSONObject().put("alert_interval_minutes", p("integer", "15, 30, 60, 180, 360"))
                    .put("alert_min_score", p("integer", "0-100 minimum match % to notify"))
                    .put("alerts_on", p("boolean", ""))
                    .put("default_country", p("string", "")).put("default_location", p("string", ""))
                    .put("model", p("string", "claude-haiku-5-5 or claude-sonnet-5-5"))))
            .put(tool("open_screen", "Show a screen to the user.",
                JSONObject().put("screen", p("string", "search, results, job, my_jobs, cvs, alerts, settings"))
                    .put("job_id", p("string", "for screen=job")), listOf("screen")))
            .put(tool("open_site", "Open a job site search inside the app (PNet, Indeed, LinkedIn, CareerJunction, Careers24, Google Jobs, Seek).",
                JSONObject().put("site", p("string", "")).put("query", p("string", "")).put("location", p("string", "")),
                listOf("site", "query")))
    }

    private fun context(): String {
        val cur = Nav.current
        val here = when (cur) {
            is Screen.Detail -> Store.job(cur.jobId)?.let { "viewing job ${it.id}: ${it.title} at ${it.company}" } ?: "viewing a job"
            is Screen.Results -> "viewing ${SearchState.results.size} search results for '${SearchState.params.query}'"
            is Screen.Web -> "viewing ${cur.title} website"
            else -> "on ${cur.javaClass.simpleName} screen"
        }
        val cvs = Store.cvs.joinToString("; ") { "${it.name} (${it.titles.take(2).joinToString("/")})" }.ifBlank { "none uploaded" }
        return "User: Khumalo-style job seeker using JobRadar app on Android. Now: $here. " +
            "CVs: $cvs. My Jobs: ${Store.tracked.size}. Alerts: ${Store.searches.size}. Default country: ${Settings.defaultCountry}."
    }

    private fun system() = "You are the assistant inside JobRadar, a job-search app. Act with the tools to do what the user asks " +
        "(search, check fit, save/track jobs, alerts, CVs, settings, open screens). Be brief: short answers, bullet points, " +
        "no repetition of tool output the user can already see on screen. Use the free local match % before check_fit. " +
        "Present the user's strengths positively; mention gaps only when asked or when deciding which CV to use. " + context()

    suspend fun ask(ctx: Context, userText: String): Pair<String, Int> {
        val history = Store.chat.toList().takeLast(6)
        val msgs = JSONArray()
        history.filter { it.role == "user" || it.role == "assistant" }.forEach {
            msgs.put(JSONObject().put("role", it.role).put("content", it.text.take(1200)))
        }
        // The API needs the first message to be from the user and roles to alternate.
        while (msgs.length() > 0 && msgs.getJSONObject(0).optString("role") != "user") msgs.remove(0)
        val clean = JSONArray()
        for (i in 0 until msgs.length()) {
            val m = msgs.getJSONObject(i)
            if (clean.length() > 0 && clean.getJSONObject(clean.length() - 1).optString("role") == m.optString("role")) {
                clean.remove(clean.length() - 1)
            }
            clean.put(m)
        }
        if (clean.length() > 0 && clean.getJSONObject(clean.length() - 1).optString("role") == "user") clean.remove(clean.length() - 1)
        clean.put(Claude.userText(userText))

        var tokens = 0
        val notes = mutableListOf<String>()
        repeat(MAX_ROUNDS) {
            val reply = Claude.send(system(), clean, maxTokens = 900, tools = tools)
            tokens += reply.inputTokens + reply.outputTokens
            if (reply.stopReason != "tool_use") {
                return reply.text.ifBlank { notes.joinToString("\n").ifBlank { "Done." } } to tokens
            }
            clean.put(JSONObject().put("role", "assistant").put("content", reply.content))
            val results = JSONArray()
            for (i in 0 until reply.content.length()) {
                val b = reply.content.optJSONObject(i) ?: continue
                if (b.optString("type") != "tool_use") continue
                val out = try {
                    run(ctx, b.optString("name"), b.optJSONObject("input") ?: JSONObject())
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                notes += "• ${b.optString("name")}"
                results.put(JSONObject().put("type", "tool_result").put("tool_use_id", b.optString("id")).put("content", out.take(3500)))
            }
            clean.put(JSONObject().put("role", "user").put("content", results))
        }
        return "I did several steps (${notes.joinToString(" ")}) — check the screen. Ask me to continue if needed." to tokens
    }

    private suspend fun run(ctx: Context, name: String, a: JSONObject): String = when (name) {
        "search_jobs" -> {
            val cvIds = a.optJSONArray("cv_names").strings().mapNotNull { Store.cvByName(it)?.id }
            val cur = SearchState.params
            val params = SearchParams(
                query = a.optString("query"),
                location = if (a.has("location")) a.optString("location") else cur.location,
                country = a.optString("country").ifBlank { cur.country }.lowercase(),
                cvIds = cvIds.ifEmpty { if (a.optString("query").isBlank()) Store.cvs.map { it.id } else emptyList() },
                daysOld = a.optInt("days_old", 0),
                salaryMin = a.optInt("salary_min", 0),
                jobType = a.optString("job_type").ifBlank { "any" },
                sort = a.optString("sort").ifBlank { "relevance" },
                remoteOnly = a.optBoolean("remote_only", false),
                sources = cur.sources,
            )
            SearchState.run(params)
            withContext(Dispatchers.Main) { Nav.go(Screen.Results) }
            val top = SearchState.results.take(12).joinToString("\n") { sj ->
                val j = sj.job
                "${j.id} | ${j.title} | ${j.company} | ${j.location} | ${j.salary} | ${sj.match.score}% (${sj.match.cvName})"
            }
            "Found ${SearchState.results.size}. ${SearchState.errors.joinToString("; ")}\n$top"
        }
        "get_job" -> Store.job(a.optString("job_id"))?.let { j ->
            "${j.title} | ${j.company} | ${j.location} | ${j.salary} | ${j.contract} | ${j.source}\n${j.description.take(1800)}\nURL: ${j.url}"
        } ?: "Job not found"
        "check_fit" -> {
            val j = Store.job(a.optString("job_id")) ?: error("Job not found")
            val f = FitChecker.check(j)
            "Score ${f.score}. Best CV: ${f.bestCv}. ${f.verdict}\nStrengths: ${f.strengths.joinToString("; ")}\n" +
                "Gaps: ${f.gaps.joinToString("; ")}\nTips: ${f.tips.joinToString("; ")}"
        }
        "list_cvs" -> Store.cvs.joinToString("\n") {
            "${it.name}: titles=${it.titles.joinToString(", ")}; skills=${it.skills.take(12).joinToString(", ")}; alerts=${it.alertOn}"
        }.ifBlank { "No CVs uploaded" }
        "update_cv" -> {
            val cv = Store.cvByName(a.optString("cv_name")) ?: error("CV not found")
            val i = Store.cvs.indexOfFirst { it.id == cv.id }
            var nu = cv
            if (a.has("titles")) nu = nu.copy(titles = a.optJSONArray("titles").strings().map { it.lowercase() })
            if (a.optString("new_name").isNotBlank()) nu = nu.copy(name = a.optString("new_name"))
            if (a.has("alerts_on")) nu = nu.copy(alertOn = a.optBoolean("alerts_on"))
            Store.cvs[i] = nu; Store.saveCvs(); "Updated ${nu.name}"
        }
        "delete_cv" -> {
            val cv = Store.cvByName(a.optString("cv_name")) ?: error("CV not found")
            Store.cvs.removeAll { it.id == cv.id }; Store.saveCvs(); "Deleted ${cv.name}"
        }
        "track_job" -> {
            val j = Store.job(a.optString("job_id")) ?: error("Job not found")
            val st = STATUSES.firstOrNull { it.equals(a.optString("status"), true) } ?: "Saved"
            Store.track(j, st, a.optString("note").ifBlank { null }); "${j.title} → $st"
        }
        "untrack_job" -> { Store.untrack(a.optString("job_id")); "Removed" }
        "list_my_jobs" -> {
            val st = a.optString("status")
            Store.tracked.filter { st.isBlank() || it.status.equals(st, true) }.joinToString("\n") {
                "${it.job.id} | ${it.status} | ${it.job.title} | ${it.job.company}" + if (it.note.isNotBlank()) " | note: ${it.note}" else ""
            }.ifBlank { "None" }
        }
        "create_alert" -> {
            val s = SavedSearch(
                UUID.randomUUID().toString(),
                a.optString("name").ifBlank { a.optString("query") },
                a.optString("query"), a.optString("location"),
                a.optString("country").ifBlank { Settings.defaultCountry }.lowercase(),
                a.optBoolean("remote_only", false), a.optInt("salary_min", 0),
            )
            withContext(Dispatchers.Main) { Store.searches.add(s) }
            Store.saveSearches(); AlertWorker.schedule(ctx); "Alert '${s.name}' created"
        }
        "delete_alert" -> {
            val n = a.optString("name")
            val removed = Store.searches.removeAll { it.name.equals(n, true) || it.query.equals(n, true) }
            Store.saveSearches(); if (removed) "Deleted" else "No alert named $n"
        }
        "list_alerts" -> "Alerts:\n" + Store.searches.joinToString("\n") { "${it.name}: '${it.query}' ${it.location} ${it.country}" } +
            "\nCV alerts: " + Store.cvs.filter { it.alertOn }.joinToString { it.name } +
            "\nRecent new matches:\n" + Store.newMatches.take(10).joinToString("\n") { m ->
                Store.job(m.jobId)?.let { "${it.id} | ${it.title} | ${it.company} | ${m.score}%" } ?: m.jobId
            }
        "run_alerts_now" -> { AlertWorker.runNow(ctx); "Checking in the background; new matches will notify." }
        "update_settings" -> {
            if (a.has("alert_interval_minutes")) Settings.alertMinutes = a.optInt("alert_interval_minutes").coerceAtLeast(15)
            if (a.has("alert_min_score")) Settings.alertMinScore = a.optInt("alert_min_score").coerceIn(0, 100)
            if (a.has("alerts_on")) Settings.alertsOn = a.optBoolean("alerts_on")
            if (a.has("default_country")) a.optString("default_country").lowercase().takeIf { c -> COUNTRIES.any { it.code == c } }?.let { Settings.defaultCountry = it }
            if (a.has("default_location")) Settings.defaultLocation = a.optString("default_location")
            if (a.has("model")) a.optString("model").takeIf { it.startsWith("claude-") }?.let { Settings.claudeModel = it }
            AlertWorker.schedule(ctx); "Settings updated"
        }
        "open_screen" -> withContext(Dispatchers.Main) {
            when (a.optString("screen")) {
                "search" -> Nav.tab(Screen.Search)
                "results" -> Nav.go(Screen.Results)
                "job" -> Nav.go(Screen.Detail(a.optString("job_id")))
                "my_jobs" -> Nav.tab(Screen.MyJobs)
                "cvs" -> Nav.tab(Screen.Cvs)
                "alerts" -> Nav.tab(Screen.Alerts)
                "settings" -> Nav.go(Screen.Settings)
            }
            "Opened"
        }
        "open_site" -> {
            val links = QuickLinks.all(a.optString("query"), a.optString("location"), SearchState.params.country)
            val hit = links.firstOrNull { it.first.equals(a.optString("site"), true) }
                ?: links.firstOrNull { it.first.contains(a.optString("site"), true) }
                ?: error("Site not available for this country")
            withContext(Dispatchers.Main) { Nav.go(Screen.Web(hit.first, hit.second)) }
            "Opened ${hit.first}. The user can tap 'Check fit' on a job page there."
        }
        else -> "Unknown tool"
    }

    /** Paste-a-CV support for the assistant chat ("here's my CV text: ..."). Not a tool, used by the CV screen. */
    fun addCvFromText(name: String, text: String) {
        Store.cvs.add(CvParser.build(name, text)); Store.saveCvs()
    }
}
