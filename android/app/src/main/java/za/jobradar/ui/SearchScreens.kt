@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package za.jobradar.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONArray
import za.jobradar.ai.Claude
import za.jobradar.ai.FitChecker
import za.jobradar.data.FitResult
import za.jobradar.data.Job
import za.jobradar.data.STATUSES
import za.jobradar.data.SavedSearch
import za.jobradar.data.ScoredJob
import za.jobradar.data.Store
import za.jobradar.search.ALL_SOURCES
import za.jobradar.search.COUNTRIES
import za.jobradar.search.JobSearch
import za.jobradar.search.Matcher
import za.jobradar.search.QuickLinks
import za.jobradar.search.country
import java.util.UUID

// ---------------------------------------------------------------- Search (home)

@Composable
fun SearchScreen() {
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(SearchState.params) }
    var advanced by remember { mutableStateOf(false) }
    var countryOpen by remember { mutableStateOf(false) }

    fun go() {
        SearchState.params = p
        Nav.go(Screen.Results)
        scope.launch { SearchState.run(p) }
    }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())) {
        // Search box, Indeed-style
        Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            OutlinedTextField(
                value = p.query, onValueChange = { p = p.copy(query = it) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Job title, keywords or company") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go() }),
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = p.location, onValueChange = { p = p.copy(location = it) },
                leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                placeholder = { Text("City, suburb or province") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go() }),
            )
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { countryOpen = true }) { Text(country(p.country).label + " ▾") }
                    DropdownMenu(expanded = countryOpen, onDismissRequest = { countryOpen = false }) {
                        COUNTRIES.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = { p = p.copy(country = c.code); countryOpen = false })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { go() }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                    Text("Find jobs", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search by CV
        WhiteCard {
            Text("Search with my CVs", fontWeight = FontWeight.Bold)
            if (Store.cvs.isEmpty()) {
                Hint("Upload your CVs in the CVs tab — then tap a CV here to find jobs that suit it.")
                TextButton(onClick = { Nav.tab(Screen.Cvs) }) { Text("Upload CVs") }
            } else {
                Hint("Pick one or more CVs and leave keywords empty to search by each CV's job titles.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = p.cvIds.size == Store.cvs.size,
                        onClick = { p = p.copy(cvIds = if (p.cvIds.size == Store.cvs.size) emptyList() else Store.cvs.map { it.id }) },
                        label = { Text("All CVs") },
                    )
                    Store.cvs.forEach { cv ->
                        FilterChip(
                            selected = cv.id in p.cvIds,
                            onClick = { p = p.copy(cvIds = if (cv.id in p.cvIds) p.cvIds - cv.id else p.cvIds + cv.id) },
                            label = { Text(cv.name.substringBeforeLast('.'), maxLines = 1) },
                        )
                    }
                }
                if (p.cvIds.isNotEmpty() && p.query.isBlank()) {
                    Hint("Will search: " + Store.cvs.filter { it.id in p.cvIds }.joinToString("; ") { JobSearch.queryForCv(it) })
                    Button(onClick = { go() }, colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                        Text("Find jobs for selected CVs")
                    }
                }
            }
        }

        // Advanced filters
        WhiteCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Advanced filters", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide" else "Show") }
            }
            if (advanced) AdvancedFilters(p) { p = it }
        }

        // Sites without an API, opened inside the app
        WhiteCard {
            Text("Also search on", fontWeight = FontWeight.Bold)
            Hint("Opens inside the app. On any job page, tap \"Check fit\" to let Claude compare it with your CVs.")
            val q = p.query.ifBlank { Store.cvs.firstOrNull { it.id in p.cvIds }?.titles?.firstOrNull() ?: "" }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickLinks.all(q, p.location, p.country).forEach { (name, url) ->
                    OutlinedButton(onClick = { Nav.go(Screen.Web(name, url)) }) { Text(name) }
                }
            }
        }

        // Recent background finds
        if (Store.newMatches.isNotEmpty()) {
            SectionTitle("  New matches found for you")
            Store.newMatches.take(5).forEach { m ->
                val job = Store.job(m.jobId) ?: return@forEach
                JobCard(ScoredJob(job, Matcher.best(job, Store.cvs.toList()))) { Nav.go(Screen.Detail(job.id)) }
            }
            TextButton(onClick = { Nav.tab(Screen.Alerts) }, modifier = Modifier.padding(start = 8.dp)) { Text("See all") }
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
fun AdvancedFilters(p: za.jobradar.data.SearchParams, onChange: (za.jobradar.data.SearchParams) -> Unit) {
    Column {
        Text("Date posted", fontSize = 13.sp, color = Grey)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Any time", 1 to "24h", 3 to "3 days", 7 to "7 days", 14 to "14 days", 30 to "30 days").forEach { (d, l) ->
                FilterChip(selected = p.daysOld == d, onClick = { onChange(p.copy(daysOld = d)) }, label = { Text(l) })
            }
        }
        Text("Job type", fontSize = 13.sp, color = Grey)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("any" to "Any", "permanent" to "Permanent", "contract" to "Contract", "full_time" to "Full-time", "part_time" to "Part-time").forEach { (k, l) ->
                FilterChip(selected = p.jobType == k, onClick = { onChange(p.copy(jobType = k)) }, label = { Text(l) })
            }
        }
        Text("Sort by", fontSize = 13.sp, color = Grey)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("relevance" to "Best for me", "match" to "Match %", "date" to "Newest", "salary" to "Salary").forEach { (k, l) ->
                FilterChip(selected = p.sort == k, onClick = { onChange(p.copy(sort = k)) }, label = { Text(l) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = if (p.salaryMin == 0) "" else p.salaryMin.toString(),
                onValueChange = { onChange(p.copy(salaryMin = it.filter(Char::isDigit).take(8).toIntOrNull() ?: 0)) },
                label = { Text("Min salary / year") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = if (p.distanceKm == 0) "" else p.distanceKm.toString(),
                onValueChange = { onChange(p.copy(distanceKm = it.filter(Char::isDigit).take(4).toIntOrNull() ?: 0)) },
                label = { Text("Within km") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            value = p.exclude, onValueChange = { onChange(p.copy(exclude = it)) },
            label = { Text("Exclude words (e.g. sales, intern)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Remote only", modifier = Modifier.weight(1f))
            Switch(checked = p.remoteOnly, onCheckedChange = { onChange(p.copy(remoteOnly = it)) })
        }
        Text("Minimum match: ${p.minMatch}%", fontSize = 13.sp, color = Grey)
        Slider(value = p.minMatch.toFloat(), onValueChange = { onChange(p.copy(minMatch = it.toInt())) }, valueRange = 0f..90f, steps = 8)
        Text("Job sources", fontSize = 13.sp, color = Grey)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ALL_SOURCES.forEach { s ->
                FilterChip(
                    selected = s in p.sources,
                    onClick = { onChange(p.copy(sources = if (s in p.sources) p.sources - s else p.sources + s)) },
                    label = { Text(s) },
                )
            }
        }
        Hint("Adzuna = SA & international boards (pulls many PNet/Careers24 adverts). Remotive/Arbeitnow = remote & Europe.")
    }
}

// ---------------------------------------------------------------- Results

@Composable
fun ResultsScreen() {
    val ctx = LocalContext.current
    var savedMsg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val p = SearchState.params
                Text(p.query.ifBlank { if (p.cvIds.isNotEmpty()) "Jobs for my CVs" else "All jobs" }, fontWeight = FontWeight.Bold)
                Text(listOf(p.location, country(p.country).label).filter { it.isNotBlank() }.joinToString(", ") +
                    if (SearchState.summary.isNotBlank()) " · " + SearchState.summary else "", fontSize = 12.sp, color = Grey)
            }
            TextButton(onClick = {
                val p = SearchState.params
                val q = p.query.ifBlank { Store.cvs.filter { it.id in p.cvIds }.joinToString(" OR ") { JobSearch.queryForCv(it) } }
                if (q.isNotBlank()) {
                    Store.searches.add(SavedSearch(UUID.randomUUID().toString(), q.take(40), q, p.location, p.country, p.remoteOnly, p.salaryMin))
                    Store.saveSearches()
                    za.jobradar.alerts.AlertWorker.schedule(ctx)
                    savedMsg = "Alert saved — you'll be notified of new matches"
                }
            }) { Text("🔔 Alert me") }
        }
        if (savedMsg.isNotBlank()) Text(savedMsg, color = Green, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        SearchState.errors.forEach { Text("⚠ $it", color = Amber, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
        if (SearchState.loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue) }
        } else if (SearchState.results.isEmpty()) {
            Text("No jobs found. Try fewer filters, another location, or turn on more sources.", color = Grey, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(SearchState.results, key = { it.job.id }) { sj -> JobCard(sj) { Nav.go(Screen.Detail(sj.job.id)) } }
        }
    }
}

// ---------------------------------------------------------------- Job detail

@Composable
fun DetailScreen(jobId: String) {
    val job = Store.job(jobId)
    if (job == null) {
        Text("This job is no longer cached. Run the search again.", modifier = Modifier.padding(16.dp)); return
    }
    JobDetailBody(job)
}

@Composable
fun JobDetailBody(job: Job) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val match = remember(job.id, Store.cvs.size) { Matcher.ranked(job, Store.cvs.toList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var statusOpen by remember { mutableStateOf(false) }
    val fit = Store.fits[job.id]
    val status = Store.statusOf(job.id)

    fun runFit(force: Boolean) {
        loading = true; error = ""
        scope.launch {
            try { FitChecker.check(job, force) } catch (e: Exception) { error = e.message ?: "Failed" }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            Text(job.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (job.company.isNotBlank()) Text(job.company, fontSize = 16.sp, color = Blue)
            Text(job.location, color = Grey)
            Spacer(Modifier.size(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (job.salary.isNotBlank()) Pill(job.salary, Color(0xFF424242))
                if (job.contract.isNotBlank()) Pill(job.contract, Color(0xFF424242))
                Pill(job.source, Blue)
                JobSearch.ago(job.postedAt).takeIf { it.isNotBlank() }?.let { Pill(it, Grey) }
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (job.url.isNotBlank()) Nav.go(Screen.Web(job.company.ifBlank { job.source }, job.url)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                ) { Text("Apply now") }
                Box {
                    OutlinedButton(onClick = { statusOpen = true }) { Text(status ?: "☆ Save") }
                    DropdownMenu(expanded = statusOpen, onDismissRequest = { statusOpen = false }) {
                        STATUSES.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { Store.track(job, s); statusOpen = false }) }
                        if (status != null) DropdownMenuItem(text = { Text("Remove from My Jobs") }, onClick = { Store.untrack(job.id); statusOpen = false })
                    }
                }
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "${job.title} — ${job.company}\n${job.url}")
                    ctx.startActivity(Intent.createChooser(i, "Share job"))
                }) { Text("Share") }
            }
        }

        // Free local match
        if (match.isNotEmpty()) WhiteCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quick match (free)", fontWeight = FontWeight.Bold)
                    Text("Best CV: ${match.first().cvName}", color = Grey, fontSize = 13.sp)
                }
                ScoreBadge(match.first().score)
            }
            if (match.first().matched.isNotEmpty()) { Text("You have", fontSize = 13.sp, color = Grey); ChipsRow(match.first().matched, Green) }
            if (match.first().missing.isNotEmpty()) { Text("Job also mentions", fontSize = 13.sp, color = Grey); ChipsRow(match.first().missing, Grey) }
            if (match.size > 1) Hint("Other CVs: " + match.drop(1).joinToString(" · ") { "${it.cvName.substringBeforeLast('.')} ${it.score}%" })
        }

        // Claude button
        WhiteCard {
            Text("Claude fit check", fontWeight = FontWeight.Bold, color = ClaudeTan)
            when {
                fit != null -> FitView(fit) { runFit(true) }
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = ClaudeTan, strokeWidth = 2.dp)
                    Text("  Claude is reading the job and your CVs…")
                }
                else -> {
                    Hint("Only runs when you tap. Sends this job + your 3 closest CVs (~2k tokens on Haiku). Result is saved, so re-opening is free.")
                    Button(
                        onClick = { if (!Claude.ready()) Nav.go(Screen.Settings) else runFit(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = ClaudeTan),
                    ) { Text(if (Claude.ready()) "✨ Check how well I qualify" else "Add Claude API key in Settings") }
                }
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFB00020), fontSize = 13.sp)
        }

        WhiteCard {
            Text("Job description", fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Text(job.description.ifBlank { "No description — tap Apply now to read the full advert." }, fontSize = 14.sp, lineHeight = 20.sp)
            if (job.source == "Adzuna" || job.source == "Careerjet") Hint("This board only shares a summary; the full advert opens with Apply now. You can tap Check fit there too.")
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
fun FitView(fit: FitResult, onRecheck: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use: ${fit.bestCv}", fontWeight = FontWeight.Medium)
                Text(fit.verdict, fontSize = 14.sp)
            }
            ScoreBadge(fit.score, "Claude")
        }
        if (fit.strengths.isNotEmpty()) { Text("Strengths", fontSize = 13.sp, color = Green, modifier = Modifier.padding(top = 6.dp)); fit.strengths.forEach { Text("• $it", fontSize = 14.sp) } }
        if (fit.gaps.isNotEmpty()) { Text("Gaps to address", fontSize = 13.sp, color = Amber, modifier = Modifier.padding(top = 6.dp)); fit.gaps.forEach { Text("• $it", fontSize = 14.sp) } }
        if (fit.tips.isNotEmpty()) { Text("How to tailor your application", fontSize = 13.sp, color = Blue, modifier = Modifier.padding(top = 6.dp)); fit.tips.forEach { Text("• $it", fontSize = 14.sp) } }
        if (fit.ranking.size > 1) Hint("CV ranking: " + fit.ranking.joinToString(" · ") { "${it.first.substringBeforeLast('.')} ${it.second}%" })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hint("${fit.tokens} tokens")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRecheck) { Text("Re-check") }
        }
    }
}

// ---------------------------------------------------------------- In-app browser (PNet, Indeed, apply pages)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(title: String, url: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf<WebView?>(null) }
    var pageJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    fun grabPage(then: (Job) -> Unit) {
        val w = web ?: return
        w.evaluateJavascript("(function(){return document.title+'\\n'+(document.body?document.body.innerText:'').substring(0,7000);})()") { raw ->
            val text = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
            val pageTitle = w.title ?: title
            val job = Job(
                id = "web:" + (w.url ?: url).hashCode().toUInt().toString(16),
                source = title, title = pageTitle.substringBefore(" | ").substringBefore(" - ").take(120),
                company = "", location = "", description = text.trim(), url = w.url ?: url, salary = "", postedAt = System.currentTimeMillis(),
            )
            Store.remember(listOf(job))
            then(job)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (!Claude.ready()) {
                        Nav.go(Screen.Settings)
                    } else {
                        busy = true; err = ""
                        grabPage { j ->
                            scope.launch {
                                try { FitChecker.check(j, force = true); pageJob = j } catch (e: Exception) { err = e.message ?: "Failed" }
                                busy = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClaudeTan),
            ) { Text(if (busy) "Checking…" else "✨ Check fit") }
            Spacer(Modifier.size(6.dp))
            OutlinedButton(onClick = { grabPage { j -> Store.track(j, "Saved"); err = "Saved to My Jobs" } }) { Text("☆ Save") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web?.url ?: url)))
            }) { Text("Browser") }
        }
        if (err.isNotBlank()) Text(err, fontSize = 12.sp, color = Amber, modifier = Modifier.padding(horizontal = 12.dp))
        pageJob?.let { j ->
            Store.fits[j.id]?.let { f ->
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).background(Color(0xFFFFF7F2)).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Claude fit check", fontWeight = FontWeight.Bold, color = ClaudeTan, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pageJob = null }) { Text("Close") }
                    }
                    FitView(f) { busy = true; scope.launch { runCatching { FitChecker.check(j, true) }; busy = false } ; Unit }
                }
                HorizontalDivider()
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                    web = this
                }
            },
        )
    }
}
