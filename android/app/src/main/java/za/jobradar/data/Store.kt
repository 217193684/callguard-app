package za.jobradar.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything the app remembers, kept as small JSON files on the phone. */
object Store {
    private lateinit var dir: File

    val cvs = mutableStateListOf<Cv>()
    val tracked = mutableStateListOf<TrackedJob>()
    val searches = mutableStateListOf<SavedSearch>()
    val newMatches = mutableStateListOf<NewMatch>()
    val chat = mutableStateListOf<ChatMsg>()
    val fits = mutableStateMapOf<String, FitResult>()

    /** Jobs we have seen recently, so a job opened from a notification still has its details. */
    private val jobs = LinkedHashMap<String, Job>()
    private val seen = LinkedHashSet<String>()

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "store").apply { mkdirs() }
        load("cvs") { cvs += Cv.fromJson(it) }
        load("tracked") { tracked += TrackedJob.fromJson(it) }
        load("searches") { searches += SavedSearch.fromJson(it) }
        load("new") { newMatches += NewMatch.fromJson(it) }
        load("chat") { chat += ChatMsg.fromJson(it) }
        load("jobs") { o -> Job.fromJson(o).let { jobs[it.id] = it } }
        load("fits") { o -> fits[o.optString("jobId")] = FitResult.fromJson(o.optJSONObject("fit") ?: JSONObject()) }
        File(dir, "seen.txt").takeIf { it.exists() }?.readLines()?.let { seen.addAll(it) }
    }

    private fun load(name: String, each: (JSONObject) -> Unit) {
        val f = File(dir, "$name.json")
        if (!f.exists()) return
        runCatching {
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) a.optJSONObject(i)?.let(each)
        }
    }

    @Synchronized
    private fun write(name: String, items: List<JSONObject>) {
        val a = JSONArray()
        items.forEach { a.put(it) }
        val tmp = File(dir, "$name.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(File(dir, "$name.json"))
    }

    fun saveCvs() = write("cvs", cvs.toList().map { it.toJson() })
    fun saveTracked() = write("tracked", tracked.toList().map { it.toJson() })
    fun saveSearches() = write("searches", searches.toList().map { it.toJson() })
    fun saveNew() = write("new", newMatches.toList().takeLast(200).map { it.toJson() })
    fun saveChat() = write("chat", chat.toList().takeLast(80).map { it.toJson() })
    fun saveFits() = write("fits", fits.toMap().entries.toList().takeLast(300).map {
        JSONObject().put("jobId", it.key).put("fit", it.value.toJson())
    })

    // ---- Jobs cache ----
    @Synchronized
    fun remember(list: Collection<Job>) {
        list.forEach { jobs[it.id] = it }
        while (jobs.size > 1500) jobs.remove(jobs.keys.first())
        write("jobs", jobs.values.map { it.toJson() })
    }

    @Synchronized
    fun job(id: String): Job? = jobs[id] ?: tracked.firstOrNull { it.job.id == id }?.job

    // ---- Seen (for alerts) ----
    @Synchronized
    fun isSeen(id: String) = id in seen

    @Synchronized
    fun markSeen(ids: Collection<String>) {
        seen.addAll(ids)
        while (seen.size > 5000) seen.remove(seen.first())
        File(dir, "seen.txt").writeText(seen.joinToString("\n"))
    }

    // ---- Tracker helpers ----
    fun track(job: Job, status: String = "Saved", note: String? = null) {
        val i = tracked.indexOfFirst { it.job.id == job.id }
        val now = System.currentTimeMillis()
        if (i >= 0) {
            val old = tracked[i]
            tracked[i] = old.copy(status = status, note = note ?: old.note, updatedAt = now)
        } else {
            tracked.add(0, TrackedJob(job, status, note ?: "", now))
        }
        remember(listOf(job))
        saveTracked()
    }

    fun untrack(jobId: String) {
        tracked.removeAll { it.job.id == jobId }
        saveTracked()
    }

    fun statusOf(jobId: String): String? = tracked.firstOrNull { it.job.id == jobId }?.status

    fun cvByName(name: String): Cv? =
        cvs.firstOrNull { it.name.equals(name, true) }
            ?: cvs.firstOrNull { it.name.contains(name, true) || name.contains(it.name.substringBeforeLast('.'), true) }
}
