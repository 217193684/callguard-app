package za.callguard.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.callguard.core.ApiClient
import za.callguard.core.BlockedNumber
import za.callguard.core.CallerLookup
import za.callguard.core.LookupResult
import za.callguard.core.PhoneUtil
import za.callguard.core.REPORT_CATEGORIES
import za.callguard.core.ScreenedCall
import za.callguard.core.Settings
import za.callguard.core.Store
import za.callguard.core.WebSearch
import za.callguard.sync.SpamSyncWorker

private val Green = Color(0xFF0B6E4F)
private val Red = Color(0xFFC62828)
private val Orange = Color(0xFFEF6C00)
private val Blue = Color(0xFF1565C0)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NUMBER = "number"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startNumber = intent?.getStringExtra(EXTRA_NUMBER)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green)) {
                Surface(Modifier.fillMaxSize()) { AppScreen(startNumber) }
            }
        }
    }
}

// ------------------------------------------------------------------ shell
@Composable
private fun AppScreen(startNumber: String?) {
    var tab by remember { mutableIntStateOf(if (startNumber != null) 1 else 0) }
    var lookupNumber by remember { mutableStateOf(startNumber) }
    var refresh by remember { mutableIntStateOf(0) }   // bump to reload lists
    val tabs = listOf("Recent", "Lookup", "Blocked", "Settings")

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            "CallGuard", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = Green,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        SetupBanner()
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i; refresh++ }, text = { Text(t) })
            }
        }
        val onChanged: () -> Unit = { refresh++ }
        when (tab) {
            0 -> RecentTab(refresh) { number -> lookupNumber = number; tab = 1 }
            1 -> LookupTab(lookupNumber, onChanged)
            2 -> BlockedTab(refresh, onChanged)
            3 -> SettingsTab()
        }
    }
}

/** Asks for the call-screening role and notification permission until both are granted. */
@Composable
private fun SetupBanner() {
    val ctx = LocalContext.current
    val roleManager = remember { ctx.getSystemService(RoleManager::class.java) }
    var hasRole by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) }
    var hasNotif by remember { mutableStateOf(notificationsGranted(ctx)) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasRole = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasNotif = it
    }
    if (hasRole && hasNotif) return

    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Finish setup", fontWeight = FontWeight.Bold)
            if (!hasRole) {
                Text("Let CallGuard screen calls so it can identify and block spam.")
                Button(onClick = {
                    roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                }) { Text("Enable call screening") }
            }
            if (!hasNotif && Build.VERSION.SDK_INT >= 33) {
                Text("Allow notifications so you can see who's calling.")
                Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Allow notifications")
                }
            }
        }
    }
}

private fun notificationsGranted(ctx: Context) =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

// ------------------------------------------------------------------ recent
@Composable
private fun RecentTab(refresh: Int, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var calls by remember { mutableStateOf(emptyList<ScreenedCall>()) }
    LaunchedEffect(refresh) { calls = withContext(Dispatchers.IO) { store.recentCalls() } }

    if (calls.isEmpty()) {
        EmptyNote("No screened calls yet. Calls from numbers that aren't in your contacts will appear here.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(calls, key = { it.id }) { c ->
            val tappable = c.number != "Hidden"
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = tappable) { onOpen(c.number) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(c.title, fontWeight = FontWeight.SemiBold)
                    val sub = buildString {
                        if (c.number != "Hidden" && c.title != PhoneUtil.pretty(c.number)) {
                            append(PhoneUtil.pretty(c.number)); append(" · ")
                        }
                        append(DateUtils.getRelativeTimeSpanString(c.at))
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                VerdictBadge(if (c.action == "blocked") "blocked" else c.verdict)
            }
            HorizontalDivider()
        }
    }
}

// ------------------------------------------------------------------ lookup
@Composable
private fun LookupTab(startNumber: String?, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(ctx) }
    val store = remember { Store.get(ctx) }
    val api = remember { ApiClient(settings) }
    var input by remember(startNumber) { mutableStateOf(startNumber?.let { PhoneUtil.pretty(it) } ?: "") }
    var result by remember { mutableStateOf<LookupResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun search() {
        val n = PhoneUtil.normalize(input, settings.region)
        if (n == null) { message = "That doesn't look like a phone number."; return }
        busy = true; message = null
        scope.launch {
            result = CallerLookup(ctx).identify(n, timeoutMs = 6000)
            if (result?.source == "none" && result?.myName == null) message =
                if (settings.hasServer) "No reports for this number yet. Tap \"Who is this?\" to search the web."
                else "Tap \"Who is this?\" to see which company or agency publishes this number online."
            busy = false
        }
    }

    // Opened from a call notification or the Recent list: look it up straight away
    LaunchedEffect(startNumber) { if (startNumber != null) search() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Phone number") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { search() }, enabled = !busy && input.isNotBlank()) {
            Text(if (busy) "Searching…" else "Search")
        }
        message?.let { Text(it, color = Color.Gray) }
        result?.let { r ->
            ResultCard(r)
            Button(onClick = { WebSearch.open(ctx, r.number) }, modifier = Modifier.fillMaxWidth()) {
                Text("Who is this? Search the web")
            }
            Text(
                "Searches Google for every way this number is written (021…, +27…). Companies, " +
                    "recruitment agencies and shops usually list their numbers online; private numbers usually aren't.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            HorizontalDivider()
            NamePanel(r) { newName ->
                if (newName.isBlank()) store.deleteName(r.number) else store.saveName(r.number, newName)
                result = r.copy(myName = newName.trim().ifBlank { null })
                message = if (newName.isBlank()) "Name removed." else "Saved. It will show when this number calls."
                onChanged()
            }
            HorizontalDivider()
            ReportPanel(r) { isSpam, category, label ->
                scope.launch {
                    if (isSpam) store.block(r.number, label ?: "Reported as spam") else store.unblock(r.number)
                    message = runCatching { api.report(r.number, isSpam, category, label) }
                        .fold(
                            { if (isSpam) "Blocked and reported." else "Marked as not spam." },
                            { "Saved on this phone, but the report didn't reach the server: ${it.message}" },
                        )
                    result = CallerLookup(ctx).identify(r.number, timeoutMs = 6000)
                    onChanged()
                }
            }
        }
    }
}

@Composable
private fun ResultCard(r: LookupResult) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title(), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                VerdictBadge(r.verdict)
            }
            Text(PhoneUtil.pretty(r.number))
            if (r.myName != null) Text("Name saved by you", color = Green)
            if (r.businessName != null) Text("Verified business: ${r.businessName}", color = Blue)
            if (r.reports > 0 || r.notSpam > 0) {
                Text("Spam likelihood ${r.score}% · ${r.reports} spam / ${r.notSpam} not-spam votes")
            }
            r.category?.let { Text("Most reported as: ${it.replace('_', ' ')}") }
            listOfNotNull(r.carrier, r.lineType?.replace('_', ' '), r.location)
                .takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), color = Color.Gray) }
            if (r.carrier != null) {
                Text("Network shown is where the number was first issued; it may have been ported.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text("Source: ${r.source}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun NamePanel(r: LookupResult, onSave: (String) -> Unit) {
    var name by remember(r.number, r.myName) { mutableStateOf(r.myName ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Name this number", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) },
            label = { Text("e.g. Recruiter – Pro Talent") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Text("Saved on this phone only. It shows on screen the next time this number calls.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save name") }
            if (r.myName != null) OutlinedButton(onClick = { onSave("") }) { Text("Remove name") }
        }
    }
}

@Composable
private fun ReportPanel(r: LookupResult, onReport: (Boolean, String, String?) -> Unit) {
    var category by remember(r.number) { mutableStateOf("scam") }
    var label by remember(r.number) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Report this number", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Type: ")
            TextButton(onClick = { menu = true }) { Text(category.replace('_', ' ')) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                REPORT_CATEGORIES.forEach { c ->
                    DropdownMenuItem(text = { Text(c.replace('_', ' ')) }, onClick = { category = c; menu = false })
                }
            }
        }
        OutlinedTextField(
            value = label, onValueChange = { label = it.take(60) },
            label = { Text("Short label (optional), e.g. \"Fake bank call\"") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Text("Describe the call, not a person: don't enter anyone's name.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onReport(true, category, label.ifBlank { null }) }) { Text("Spam + block") }
            OutlinedButton(onClick = { onReport(false, "other", null) }) { Text("Not spam") }
        }
    }
}

// ------------------------------------------------------------------ blocked
@Composable
private fun BlockedTab(refresh: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val settings = remember { Settings(ctx) }
    var list by remember { mutableStateOf(emptyList<BlockedNumber>()) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh) { list = withContext(Dispatchers.IO) { store.blockedList() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, singleLine = true,
                label = { Text("Block a number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val n = PhoneUtil.normalize(input, settings.region)
                if (n == null) {
                    error = "Not a valid number"
                } else {
                    store.block(n, "Added by you"); input = ""; error = null; onChanged()
                }
            }) { Text("Block") }
        }
        error?.let { Text(it, color = Red, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (list.isEmpty()) EmptyNote("Nothing blocked yet.") else LazyColumn {
            items(list, key = { it.number }) { b ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(PhoneUtil.pretty(b.number), fontWeight = FontWeight.SemiBold)
                        b.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                    }
                    TextButton(onClick = { store.unblock(b.number); onChanged() }) { Text("Unblock") }
                }
                HorizontalDivider()
            }
        }
    }
}

// ------------------------------------------------------------------ settings
@Composable
private fun SettingsTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(ctx) }
    var url by remember { mutableStateOf(settings.serverUrl) }
    var region by remember { mutableStateOf(settings.region) }
    var autoBlock by remember { mutableStateOf(settings.autoBlockSpam) }
    var blockHidden by remember { mutableStateOf(settings.blockHidden) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true,
            label = { Text("Server address (optional)") },
            placeholder = { Text("http://192.168.1.23:8000") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = region, onValueChange = { region = it.take(2) }, singleLine = true,
            label = { Text("Country code for local numbers (ZA, GB, …)") }, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                settings.serverUrl = url; settings.region = region
                status = "Testing…"
                scope.launch {
                    status = runCatching { ApiClient(settings).health() }.fold(
                        { if (it) "Connected ✔" else "Server answered oddly" },
                        { "Can't reach server: ${it.message}" },
                    )
                }
            }) { Text("Save & test") }
            OutlinedButton(onClick = {
                SpamSyncWorker.runNow(ctx); status = "Downloading the spam list in the background…"
            }) { Text("Sync spam list") }
        }
        status?.let { Text(it) }
        HorizontalDivider()
        ToggleRow("Block numbers the community marks as spam", autoBlock) {
            autoBlock = it; settings.autoBlockSpam = it
        }
        ToggleRow("Block hidden / private numbers", blockHidden) {
            blockHidden = it; settings.blockHidden = it
        }
        HorizontalDivider()
        Text(
            "CallGuard never uploads your contacts. Numbers saved in your contacts always ring through. " +
                "Reports are sent with a random ID, not your number.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
        )
    }
}

// ------------------------------------------------------------------ bits
@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun VerdictBadge(verdict: String) {
    val (text, color) = when (verdict) {
        "spam" -> "Spam" to Red
        "blocked" -> "Blocked" to Red
        "suspicious" -> "Suspicious" to Orange
        "hidden" -> "Hidden" to Color.Gray
        else -> "No reports" to Green
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun EmptyNote(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(text, color = Color.Gray)
    }
}
