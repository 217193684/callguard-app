package za.jobradar.ai

import org.json.JSONArray
import za.jobradar.data.FitResult
import za.jobradar.data.Job
import za.jobradar.data.Store
import za.jobradar.data.strings
import za.jobradar.search.Matcher

/**
 * The "Check my fit" button. Token-saving tricks:
 *  - the phone pre-ranks your CVs for free and only the top 3 are sent
 *  - each CV is sent as a compact profile (skills + first part of the text), not the whole file
 *  - the job description is trimmed
 *  - the answer is short JSON, and is cached so tapping again costs nothing
 */
object FitChecker {
    private const val SYSTEM = "You are a blunt, practical South African recruiter. Judge how well the candidate fits " +
        "the job using ONLY the CV profiles given. Reply with JSON only, no prose: " +
        "{\"score\":0-100,\"best_cv\":\"exact CV name\",\"verdict\":\"one sentence\"," +
        "\"strengths\":[max 4 short],\"gaps\":[max 4 short],\"tips\":[max 3 short, how to tailor the CV/application]," +
        "\"ranking\":[{\"cv\":\"name\",\"score\":0-100}]}"

    suspend fun check(job: Job, force: Boolean = false): FitResult {
        if (!force) Store.fits[job.id]?.let { return it }
        val cvs = Store.cvs.toList()
        require(cvs.isNotEmpty()) { "Upload at least one CV first" }

        val top = Matcher.ranked(job, cvs).take(3).mapNotNull { m -> cvs.firstOrNull { it.id == m.cvId } }
        val cvBlock = top.joinToString("\n\n") { cv ->
            "CV \"${cv.name}\"\nSkills: ${cv.skills.take(25).joinToString(", ")}\nTitles: ${cv.titles.joinToString(", ")}\n" +
                "Text: " + cv.text.replace(Regex("\\s+"), " ").take(1600)
        }
        val prompt = "JOB: ${job.title} — ${job.company} (${job.location})\n" +
            (if (job.salary.isNotBlank()) "Salary: ${job.salary}\n" else "") +
            "Description: " + job.description.replace(Regex("\\s+"), " ").take(2500) +
            "\n\nCANDIDATE CVs:\n" + cvBlock

        val reply = Claude.send(SYSTEM, JSONArray().put(Claude.userText(prompt)), maxTokens = 500)
        val o = Claude.extractJson(reply.text) ?: error("Claude didn't return a readable answer — try again")
        val rank = o.optJSONArray("ranking") ?: JSONArray()
        val fit = FitResult(
            score = o.optInt("score"),
            bestCv = o.optString("best_cv"),
            verdict = o.optString("verdict"),
            strengths = o.optJSONArray("strengths").strings(),
            gaps = o.optJSONArray("gaps").strings(),
            tips = o.optJSONArray("tips").strings(),
            ranking = (0 until rank.length()).mapNotNull { i -> rank.optJSONObject(i)?.let { it.optString("cv") to it.optInt("score") } },
            tokens = reply.inputTokens + reply.outputTokens,
            at = System.currentTimeMillis(),
        )
        Store.fits[job.id] = fit
        Store.remember(listOf(job))
        Store.saveFits()
        return fit
    }
}
