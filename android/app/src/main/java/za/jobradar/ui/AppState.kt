package za.jobradar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import za.jobradar.data.ScoredJob
import za.jobradar.data.SearchParams
import za.jobradar.data.Settings
import za.jobradar.search.JobSearch

sealed class Screen(val tab: String) {
    data object Search : Screen("search")
    data object Results : Screen("search")
    data class Detail(val jobId: String) : Screen("search")
    data object MyJobs : Screen("myjobs")
    data object Cvs : Screen("cvs")
    data object Alerts : Screen("alerts")
    data object Assistant : Screen("claude")
    data object Settings : Screen("search")
    data class Web(val title: String, val url: String) : Screen("search")
}

object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Search)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { if (stack.last() != s) stack.add(s) }
    fun tab(s: Screen) { stack.clear(); stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
}

object SearchState {
    var params by mutableStateOf(SearchParams())
    val results = mutableStateListOf<ScoredJob>()
    val errors = mutableStateListOf<String>()
    var summary by mutableStateOf("")
    var loading by mutableStateOf(false)
    private var initialised = false

    fun initDefaults() {
        if (initialised) return
        initialised = true
        params = params.copy(country = Settings.defaultCountry, location = Settings.defaultLocation)
    }

    suspend fun run(p: SearchParams = params) {
        params = p
        loading = true
        errors.clear()
        try {
            val out = JobSearch.search(p)
            results.clear()
            results.addAll(out.jobs)
            errors.addAll(out.errors)
            summary = "${out.jobs.size} jobs" +
                if (out.perSource.isNotEmpty()) " · " + out.perSource.entries.joinToString(" · ") { "${it.key} ${it.value}" } else ""
        } catch (e: Exception) {
            errors.add(e.message ?: "Search failed")
        } finally {
            loading = false
        }
    }
}
