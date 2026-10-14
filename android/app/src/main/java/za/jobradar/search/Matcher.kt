package za.jobradar.search

import za.jobradar.cv.Skills
import za.jobradar.data.Cv
import za.jobradar.data.Job
import za.jobradar.data.Match

/**
 * Free on-phone scoring (no Claude tokens). Looks at which skills the job asks for,
 * how many of them each CV has, and how close the job title is to the CV's titles.
 */
object Matcher {
    private val STOP = setOf("senior", "junior", "lead", "the", "and", "of", "for", "a", "an", "in", "-", "&", "/", "i", "ii", "iii")

    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9+#]+")).filter { it.length > 1 && it !in STOP }.toSet()

    fun score(job: Job, cv: Cv): Match {
        val jobText = job.title + "\n" + job.description
        val jobSkills = Skills.skillSetIn(jobText)
        val cvSkills = cv.skills.toSet()
        val matched = jobSkills.filter { it in cvSkills }
        val missing = jobSkills.filter { it !in cvSkills }

        val skillScore = if (jobSkills.isEmpty()) 0.4 else matched.size.toDouble() / jobSkills.size

        val jt = words(job.title)
        val titleScore = if (jt.isEmpty()) 0.0 else {
            val fromTitles = cv.titles.maxOfOrNull { t ->
                val tw = words(t)
                if (tw.isEmpty()) 0.0 else (jt intersect tw).size.toDouble() / tw.size
            } ?: 0.0
            // Fall back to "is the job title's wording in my CV at all?"
            val cvWords = words(cv.text.take(6000))
            val coverage = (jt intersect cvWords).size.toDouble() / jt.size
            maxOf(fromTitles, coverage * 0.8)
        }

        var s = 60 * skillScore + 40 * titleScore
        if (jobSkills.size >= 3 && matched.size >= 3) s += 5
        return Match(s.toInt().coerceIn(0, 99), cv.id, cv.name, matched, missing)
    }

    fun best(job: Job, cvs: List<Cv>): Match {
        if (cvs.isEmpty()) return Match(0, null, "", emptyList(), Skills.skillSetIn(job.title + " " + job.description).toList())
        return cvs.map { score(job, it) }.maxBy { it.score }
    }

    fun ranked(job: Job, cvs: List<Cv>): List<Match> = cvs.map { score(job, it) }.sortedByDescending { it.score }
}
