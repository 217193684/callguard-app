@file:OptIn(ExperimentalMaterial3Api::class)

package za.jobradar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import za.jobradar.data.Store

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SearchState.initDefaults()
        handle(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue, onPrimary = Color.White, secondary = Orange,
                    background = Bg, surface = Color.White,
                    secondaryContainer = Color(0xFFDCE6F7), onSecondaryContainer = DarkBlue,
                )
            ) { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val id = i?.getStringExtra("job_id")
        when {
            id != null -> { Nav.tab(Screen.Search); Nav.go(Screen.Detail(id)) }
            i?.getStringExtra("open") == "alerts" -> Nav.tab(Screen.Alerts)
        }
    }
}

private fun titleFor(s: Screen): String = when (s) {
    Screen.Search -> "JobRadar"
    Screen.Results -> "Results"
    is Screen.Detail -> Store.job(s.jobId)?.title ?: "Job"
    Screen.MyJobs -> "My Jobs"
    Screen.Cvs -> "My CVs"
    Screen.Alerts -> "Job Alerts"
    Screen.Assistant -> "Ask Claude"
    Screen.Settings -> "Settings"
    is Screen.Web -> s.title
}

@Composable
fun AppRoot() {
    val cur = Nav.current
    BackHandler(enabled = Nav.stack.size > 1) { Nav.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(cur), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (Nav.stack.size > 1) IconButton(onClick = { Nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (cur != Screen.Settings) IconButton(onClick = { Nav.go(Screen.Settings) }) {
                        Icon(Icons.Filled.Settings, "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBlue, titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue, indicatorColor = Color(0xFFDCE6F7))
                NavigationBarItem(
                    selected = cur.tab == "search", onClick = { Nav.tab(Screen.Search) },
                    icon = { Icon(Icons.Filled.Search, null) }, label = { Text("Jobs") }, colors = colors,
                )
                NavigationBarItem(
                    selected = cur.tab == "myjobs", onClick = { Nav.tab(Screen.MyJobs) },
                    icon = { Icon(Icons.Filled.Star, null) }, label = { Text("My Jobs") }, colors = colors,
                )
                NavigationBarItem(
                    selected = cur.tab == "cvs", onClick = { Nav.tab(Screen.Cvs) },
                    icon = { Icon(Icons.Filled.Person, null) }, label = { Text("CVs") }, colors = colors,
                )
                NavigationBarItem(
                    selected = cur.tab == "alerts", onClick = { Nav.tab(Screen.Alerts) },
                    icon = {
                        BadgedBox(badge = { if (Store.newMatches.isNotEmpty()) Badge { Text("${Store.newMatches.size.coerceAtMost(99)}") } }) {
                            Icon(Icons.Filled.Notifications, null)
                        }
                    },
                    label = { Text("Alerts") }, colors = colors,
                )
                NavigationBarItem(
                    selected = cur.tab == "claude", onClick = { Nav.tab(Screen.Assistant) },
                    icon = { Icon(Icons.Filled.Face, null) }, label = { Text("Claude") }, colors = colors,
                )
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (cur) {
                Screen.Search -> SearchScreen()
                Screen.Results -> ResultsScreen()
                is Screen.Detail -> DetailScreen(cur.jobId)
                Screen.MyJobs -> MyJobsScreen()
                Screen.Cvs -> CvsScreen()
                Screen.Alerts -> AlertsScreen()
                Screen.Assistant -> AssistantScreen()
                Screen.Settings -> SettingsScreen()
                is Screen.Web -> key(cur.url) { WebScreen(cur.title, cur.url) }
            }
        }
    }
}
