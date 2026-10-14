@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package za.jobradar.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.jobradar.ai.Assistant
import za.jobradar.ai.Claude
import za.jobradar.ai.MODELS
import za.jobradar.alerts.AlertWorker
import za.jobradar.cv.CvParser
import za.jobradar.data.ChatMsg
import za.jobradar.data.Cv
import za.jobradar.data.STATUSES
import za.jobradar.data.ScoredJob
import za.jobradar.data.Settings
import za.jobradar.data.Store
import za.jobradar.search.COUNTRIES
import za.jobradar.search.JobSearch
import za.jobradar.search.Matcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- My Jobs (tracker)

@Composable
fun MyJobsScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val status = STATUSES[tab]
    val list = Store.tracked.filter { it.status == status }
    Column(Modifier.fillMaxSize().background(Bg)) {
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = Blue, edgePadding = 8.dp) {
            STATUSES.forEachIndexed { i, s ->
                val n = Store.tracked.count { it.status == s }
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(if (n > 0) "$s ($n)" else s) })
            }
        }
        if (list.isEmpty()) {
            Text(
                if (tab == 0) "Tap ☆ Save on any job to keep it here. Move it to Applied, Interview, Offer as you go."
                else "Nothing in $status yet.",
                color = Grey, modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(list, key = { it.job.id }) { t ->
                var menu by remember { mutableStateOf(false) }
                var noteOpen by remember { mutableStateOf(false) }
                Box {
                    JobCard(ScoredJob(t.job, Matcher.best(t.job, Store.cvs.toList()))) { Nav.go(Screen.Detail(t.job.id)) }
                }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (t.note.isNotBlank()) Text("📝 ${t.note}", fontSize = 13.sp, color = Grey, modifier = Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    TextButton(onClick = { noteOpen = true }) { Text("Note") }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("Move to ▾") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            STATUSES.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { Store.track(t.job, s); menu = false }) }
                            DropdownMenuItem(text = { Text("Remove") }, onClick = { Store.untrack(t.job.id); menu = false })
                        }
                    }
                }
                if (noteOpen) {
                    var txt by remember { mutableStateOf(t.note) }
                    AlertDialog(
                        onDismissRequest = { noteOpen = false },
                        title = { Text("Note") },
                        text = { OutlinedTextField(value = txt, onValueChange = { txt = it }, placeholder = { Text("e.g. Interview Tue 10:00 with HR") }) },
                        confirmButton = { TextButton(onClick = { Store.track(t.job, t.status, txt); noteOpen = false }) { Text("Save") } },
                        dismissButton = { TextButton(onClick = { noteOpen = false }) { Text("Cancel") } },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- CVs

@Composable
fun CvsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Cv?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val msgs = mutableListOf<String>()
            val added = mutableListOf<Cv>()
            withContext(Dispatchers.IO) {
                uris.forEach { u ->
                    try {
                        val cv = CvParser.parse(ctx, u)
                        added += cv
                        msgs += "✓ ${cv.name}: ${cv.skills.size} skills"
                    } catch (e: Throwable) {
                        msgs += "✗ ${runCatching { CvParser.displayName(ctx, u) }.getOrDefault("file")}: ${e.message}"
                    }
                }
            }
            added.forEach { cv ->
                // Re-uploading a file with the same name replaces the old version.
                Store.cvs.removeAll { it.name == cv.name }
                Store.cvs.add(cv)
            }
            Store.saveCvs()
            log = msgs.joinToString("\n")
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())) {
        WhiteCard {
            Text("My CVs", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Hint("Select several PDF or Word (.docx) CVs at once. They stay on your phone; only the Claude buttons send a short summary.")
            Button(
                onClick = {
                    picker.launch(arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/msword", "text/plain",
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text(if (busy) "Reading CVs…" else "⬆ Upload CVs") }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Blue)
            if (log.isNotBlank()) Text(log, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Store.cvs.forEach { cv ->
            WhiteCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cv.name, fontWeight = FontWeight.Bold)
                        Text("${cv.text.split(Regex("\\s+")).size} words · ${cv.skills.size} skills", fontSize = 12.sp, color = Grey)
                    }
                    IconButton(onClick = { Store.cvs.removeAll { it.id == cv.id }; Store.saveCvs() }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = Grey)
                    }
                }
                Text("Searches for", fontSize = 13.sp, color = Grey, modifier = Modifier.padding(top = 6.dp))
                ChipsRow(cv.titles.ifEmpty { listOf("(none — tap Edit titles)") }, Blue)
                Text("Skills found", fontSize = 13.sp, color = Grey, modifier = Modifier.padding(top = 6.dp))
                ChipsRow(cv.skills.take(18), Green)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Job alerts for this CV", modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = cv.alertOn, onCheckedChange = { on ->
                        val i = Store.cvs.indexOfFirst { it.id == cv.id }
                        if (i >= 0) { Store.cvs[i] = cv.copy(alertOn = on); Store.saveCvs() }
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val p = SearchState.params.copy(query = "", cvIds = listOf(cv.id))
                        Nav.go(Screen.Results)
                        scope.launch { SearchState.run(p) }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text("Find jobs") }
                    OutlinedButton(onClick = { editing = cv }) { Text("Edit titles") }
                }
            }
        }
        Spacer(Modifier.size(24.dp))
    }

    editing?.let { cv ->
        var txt by remember(cv.id) { mutableStateOf(cv.titles.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Job titles to search for") },
            text = {
                Column {
                    Hint("Comma-separated. These drive \"Find jobs\" and alerts for ${cv.name}.")
                    OutlinedTextField(value = txt, onValueChange = { txt = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val i = Store.cvs.indexOfFirst { it.id == cv.id }
                    if (i >= 0) {
                        Store.cvs[i] = cv.copy(titles = txt.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() })
                        Store.saveCvs()
                    }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Alerts

@Composable
fun AlertsScreen() {
    val ctx = LocalContext.current
    val v = Settings.version // re-read settings on change
    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    var permAsked by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permAsked = true }
    val fmt = remember { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item {
            WhiteCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Live job alerts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (Settings.alertsOn) "Checking every ${Settings.alertMinutes} min · notify at ${Settings.alertMinScore}%+ match" else "Off",
                            fontSize = 13.sp, color = Grey,
                        )
                    }
                    Switch(checked = Settings.alertsOn, onCheckedChange = { Settings.alertsOn = it; AlertWorker.schedule(ctx) })
                }
                if (needsPermission && !permAsked) {
                    Button(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text("Allow notifications") }
                }
                Text(
                    "Last check: " + (if (Settings.lastAlertRun == 0L) "not yet (first check only learns current jobs, then alerts on new ones)" else fmt.format(Date(Settings.lastAlertRun))) +
                        if (Settings.lastAlertError.isNotBlank()) "\nLast error: ${Settings.lastAlertError}" else "",
                    fontSize = 12.sp, color = Grey, modifier = Modifier.padding(top = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { AlertWorker.runNow(ctx) }) { Text("Check now") }
                    TextButton(onClick = { Nav.go(Screen.Settings) }) { Text("Frequency & threshold") }
                }
                Hint("Uses no Claude tokens. Alerts run for every CV with alerts on, plus the saved searches below. Tip: tap 🔔 Alert me on any search results.")
                if (v < 0) Text("") // keeps 'v' read so this card refreshes
            }
        }
        item { SectionTitle("  Saved searches") }
        if (Store.searches.isEmpty()) item { Hint("    None yet — run a search and tap 🔔 Alert me.") }
        items(Store.searches, key = { it.id }) { s ->
            WhiteCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.Medium)
                        Text(listOf(s.query, s.location, s.country.uppercase(), if (s.remoteOnly) "remote" else "").filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 12.sp, color = Grey)
                    }
                    Switch(checked = s.enabled, onCheckedChange = { on ->
                        val i = Store.searches.indexOfFirst { it.id == s.id }
                        if (i >= 0) { Store.searches[i] = s.copy(enabled = on); Store.saveSearches() }
                    })
                    IconButton(onClick = { Store.searches.removeAll { it.id == s.id }; Store.saveSearches() }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = Grey)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("  New matches (${Store.newMatches.size})")
                Spacer(Modifier.weight(1f))
                if (Store.newMatches.isNotEmpty()) TextButton(onClick = { Store.newMatches.clear(); Store.saveNew() }) { Text("Clear") }
            }
        }
        items(Store.newMatches.toList(), key = { it.jobId + it.foundAt }) { m ->
            val job = Store.job(m.jobId)
            if (job != null) {
                JobCard(ScoredJob(job, Matcher.best(job, Store.cvs.toList()))) { Nav.go(Screen.Detail(job.id)) }
                Text("   found ${fmt.format(Date(m.foundAt))} via ${m.via}", fontSize = 11.sp, color = Grey)
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

// ---------------------------------------------------------------- Claude assistant

@Composable
fun AssistantScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Store.chat.size) { if (Store.chat.isNotEmpty()) listState.animateScrollToItem(Store.chat.size - 1) }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        if (!Claude.ready()) { Nav.go(Screen.Settings); return }
        Store.chat.add(ChatMsg("user", text))
        input = ""
        busy = true
        scope.launch {
            try {
                val (reply, tokens) = Assistant.ask(ctx, text)
                Store.chat.add(ChatMsg("assistant", reply, tokens))
            } catch (e: Exception) {
                Store.chat.add(ChatMsg("error", e.message ?: "Something went wrong"))
            }
            Store.saveChat()
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Claude", fontWeight = FontWeight.Bold, color = ClaudeTan)
                Text("${MODELS.firstOrNull { it.first == Settings.claudeModel }?.second ?: Settings.claudeModel} · only runs when you send",
                    fontSize = 11.sp, color = Grey)
            }
            if (Store.chat.isNotEmpty()) TextButton(onClick = { Store.chat.clear(); Store.saveChat() }) { Text("New chat") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            if (Store.chat.isEmpty()) item {
                Column(Modifier.padding(16.dp)) {
                    Text("Ask me to do anything in JobRadar:", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.size(8.dp))
                    listOf(
                        "Find GIS and fibre planning jobs in Cape Town posted this week",
                        "Search jobs for all my CVs in New Zealand",
                        "Which of my saved jobs fits me best?",
                        "Create an alert for draughtsman jobs in Durban",
                        "Check my fit for the job I'm viewing",
                        "Move the Vodacom job to Interview",
                    ).forEach { s ->
                        OutlinedButton(onClick = { send(s) }, modifier = Modifier.padding(vertical = 3.dp)) { Text(s, fontSize = 13.sp) }
                    }
                    Hint("Tip: each message costs tokens. Searching, matching and alerts are free — use me for decisions and multi-step jobs.")
                }
            }
            items(Store.chat.toList()) { m -> ChatBubble(m) }
            if (busy) item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = ClaudeTan, strokeWidth = 2.dp)
                    Text("  Working…", color = Grey)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Claude…") }, maxLines = 4,
            )
            IconButton(onClick = { send(input) }, enabled = !busy) { Icon(Icons.Filled.Send, "Send", tint = ClaudeTan) }
        }
    }
}

@Composable
fun ChatBubble(m: ChatMsg) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp))
                .background(when (m.role) { "user" -> Blue; "error" -> Color(0xFFFDECEA); else -> Color.White })
                .padding(10.dp)
        ) {
            Text(m.text, color = if (mine) Color.White else Color(0xFF2D2D2D), fontSize = 14.sp)
            if (m.tokens > 0) Text("${m.tokens} tokens", fontSize = 10.sp, color = Grey)
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Settings.version
    var claudeKey by remember { mutableStateOf(Settings.claudeKey) }
    var adzId by remember { mutableStateOf(Settings.adzunaId) }
    var adzKey by remember { mutableStateOf(Settings.adzunaKey) }
    var cjKey by remember { mutableStateOf(Settings.careerjetKey) }
    var loc by remember { mutableStateOf(Settings.defaultLocation) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())) {
        WhiteCard {
            Text("Claude (AI)", fontWeight = FontWeight.Bold, color = ClaudeTan)
            Hint("Get a key at console.anthropic.com → API keys. It's billed separately from the Claude app subscription; add a small credit and set a monthly limit there.")
            OutlinedTextField(value = claudeKey, onValueChange = { claudeKey = it }, label = { Text("Anthropic API key (sk-ant-…)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            MODELS.forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = Settings.claudeModel == id, onClick = { Settings.claudeModel = id })
                    Text(label, fontSize = 14.sp)
                }
            }
            Text("Used so far: ${Settings.claudeCalls} calls · ${Settings.tokensIn} in / ${Settings.tokensOut} out tokens",
                fontSize = 12.sp, color = Grey)
            TextButton(onClick = { Settings.resetUsage() }) { Text("Reset counter") }
        }
        WhiteCard {
            Text("Job sources", fontWeight = FontWeight.Bold)
            Hint("Adzuna (free): developer.adzuna.com → register → copy App ID and App Key. Same keys your PC job radar uses.")
            OutlinedTextField(value = adzId, onValueChange = { adzId = it }, label = { Text("Adzuna App ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = adzKey, onValueChange = { adzKey = it }, label = { Text("Adzuna App Key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Hint("Careerjet (optional, free): careerjet.com/partners/api → sign up → API key.")
            OutlinedTextField(value = cjKey, onValueChange = { cjKey = it }, label = { Text("Careerjet API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }
        WhiteCard {
            Text("Defaults & alerts", fontWeight = FontWeight.Bold)
            Text("Home country", fontSize = 13.sp, color = Grey)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                COUNTRIES.forEach { c ->
                    FilterChip(selected = Settings.defaultCountry == c.code, onClick = { Settings.defaultCountry = c.code }, label = { Text(c.label) })
                }
            }
            OutlinedTextField(value = loc, onValueChange = { loc = it }, label = { Text("Default location for alerts (e.g. Cape Town)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Check for new jobs every", fontSize = 13.sp, color = Grey, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 180 to "3 hours", 360 to "6 hours").forEach { (m, l) ->
                    FilterChip(selected = Settings.alertMinutes == m, onClick = { Settings.alertMinutes = m; AlertWorker.schedule(ctx) }, label = { Text(l) })
                }
            }
            Text("Only notify at match of", fontSize = 13.sp, color = Grey)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 45, 55, 65, 75).forEach { s ->
                    FilterChip(selected = Settings.alertMinScore == s, onClick = { Settings.alertMinScore = s }, label = { Text("$s%+") })
                }
            }
            Hint("Android may delay checks a little to save battery; 15–60 min is about as live as phones allow.")
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                Settings.claudeKey = claudeKey; Settings.adzunaId = adzId; Settings.adzunaKey = adzKey
                Settings.careerjetKey = cjKey; Settings.defaultLocation = loc
                SearchState.params = SearchState.params.copy(location = SearchState.params.location.ifBlank { loc })
                AlertWorker.schedule(ctx)
                saved = true
            }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("Save settings") }
            if (saved) Text("  Saved ✓", color = Green)
        }
        Spacer(Modifier.size(24.dp))
    }
}

