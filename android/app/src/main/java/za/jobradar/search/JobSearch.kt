package za.jobradar.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import za.jobradar.data.Cv
import za.jobradar.data.Job
import za.jobradar.data.ScoredJob
import za.jobradar.data.SearchParams
import za.jobradar.data.Store

data class SearchOutcome(val jobs: List<ScoredJob>, val errors: List<String>, val perSource: Map<String, Int>)

val ALL_SOURCES = listOf("Adzuna", "Careerjet", "Remotive", "Arbeitnow")

object JobSearch {

    /** Builds a search query from a CV: its detected job titles, OR-ed together. */
    fun queryForCv(cv: Cv): String = cv.titles.take(3).ifEmpty { cv.skills.take(2) }.joinToString(" OR ")

    /**
     * Runs every enabled source in parallel. If CVs are selected and no keywords are typed,
     * each CV gets its own search built from its titles; results are merged and scored.
     */
    suspend fun search(p: SearchParams): SearchOutcome = withContext(Dispatchers.IO) {
        val allCvs = Store.cvs.toList()
        val chosenCvs = allCvs.filter { it.id in p.cvIds }
        val queries: List<Pair<String, SearchParams>> =
            if (p.query.isBlank() && chosenCvs.isNotEmpty())
                chosenCvs.map { cv -> cv.name to p.copy(query = queryForCv(cv)) }
            else listOf("" to p)

        val errors = mutableListOf<String>()
        val perSource = linkedMapOf<String, Int>()
        val raw = coroutineScope {
            queries.flatMap { (label, q) ->
                p.sources.map { src ->
                    async {
                        try {
                            val list = when (src) {
                                "Adzuna" -> Sources.adzuna(q)
                                "Careerjet" -> Sources.careerjet(q)
                                "Remotive" -> if (q.remoteOnly || q.country != "za" || p.sources.size == 1) Sources.remotive(q) else Sources.remotive(q).take(15)
                                "Arbeitnow" -> Sources.arbeitnow(q)
                                else -> emptyList()
                            }
                            synchronized(perSource) { perSource[src] = (perSource[src] ?: 0) + list.size }
                            list
                        } catch (e: Exception) {
                            val msg = "$src${if (label.isNotBlank()) " ($label)" else ""}: ${e.message ?: e.javaClass.simpleName}"
                            synchronized(errors) { if (errors.none { it.startsWith(src) && it.endsWith(e.message ?: "") }) errors += msg }
                            emptyList<Job>()
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        // De-duplicate the same advert coming from two boards.
        val unique = LinkedHashMap<String, Job>()
        raw.forEach { j ->
            val key = (j.title + "|" + j.company).lowercase().replace(Regex("[^a-z0-9|]"), "")
            if (key !in unique) unique[key] = j
        }
        val excluded = p.exclude.lowercase().split(Regex("[,\\s]+")).filter { it.length > 2 }
        val scoreCvs = chosenCvs.ifEmpty { allCvs }
        var scored = unique.values
            .filter { j -> excluded.none { j.title.lowercase().contains(it) } }
            .filter { j -> p.daysOld <= 0 || j.postedAt == 0L || j.postedAt > System.currentTimeMillis() - p.daysOld * 86_400_000L }
            .map { ScoredJob(it, Matcher.best(it, scoreCvs)) }
            .filter { it.match.score >= p.minMatch }

        scored = when (p.sort) {
            "date" -> scored.sortedByDescending { it.job.postedAt }
            "match" -> scored.sortedByDescending { it.match.score }
            else -> if (allCvs.isNotEmpty()) scored.sortedByDescending { it.match.score * 2 + recency(it.job) } else scored
        }
        Store.remember(scored.map { it.job })
        SearchOutcome(scored, errors, perSource)
    }

    private fun recency(j: Job): Int {
        if (j.postedAt == 0L) return 0
        val days = (System.currentTimeMillis() - j.postedAt) / 86_400_000L
        return (30 - days).toInt().coerceIn(0, 30)
    }

    fun ago(t: Long): String {
        if (t <= 0) return ""
        val m = (System.currentTimeMillis() - t) / 60000
        return when {
            m < 60 -> "${m.coerceAtLeast(1)}m ago"
            m < 1440 -> "${m / 60}h ago"
            m < 1440 * 30 -> "${m / 1440}d ago"
            else -> "30+ days ago"
        }
    }
}
