package com.pacelog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pacelog.app.ui.AppViewModel
import com.pacelog.app.ui.screens.ActivityFormScreen
import com.pacelog.app.ui.screens.BlockDetailScreen
import com.pacelog.app.ui.screens.BlockFormScreen
import com.pacelog.app.ui.screens.DashboardScreen
import com.pacelog.app.ui.screens.RunDetailScreen
import com.pacelog.app.ui.screens.RunsScreen
import com.pacelog.app.ui.screens.SettingsScreen
import com.pacelog.app.ui.screens.StatsScreen
import com.pacelog.app.ui.screens.TrainingScreen
import com.pacelog.app.ui.theme.PacelogTheme
import com.pacelog.app.ui.theme.Pal
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PacelogTheme { PacelogApp() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Painter)

private val TABS = listOf(
    Tab("home", "Dashboard") { rememberVectorPainter(Icons.Default.Home) },
    Tab("training", "Training") { rememberVectorPainter(Icons.Default.DateRange) },
    Tab("runs", "Runs") { rememberVectorPainter(Icons.AutoMirrored.Filled.List) },
    Tab("stats", "Statistics") { painterResource(R.drawable.ic_chart) },
)

@Composable
fun PacelogApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val snapshot by vm.state.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = TABS.any { it.route == route }

    Scaffold(
        containerColor = Pal.Paper,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)) {
                                append("PACE")
                                withStyle(SpanStyle(color = Pal.Ember)) { append("LOG") }
                            }
                        },
                        fontSize = 22.sp,
                    )
                },
                navigationIcon = {
                    if (!isTab && route != null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (route != "settings") {
                        IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings & backup")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Pal.Paper),
            )
        },
        bottomBar = {
            if (isTab) {
                NavigationBar(containerColor = Color.White) {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon(), contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = { Text(t.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Pal.EmberDark, selectedTextColor = Pal.EmberDark,
                                indicatorColor = Pal.Ember50),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isTab) {
                FloatingActionButton(onClick = { nav.navigate("log") }, containerColor = Pal.Ember, contentColor = Color.White) {
                    Icon(Icons.Default.Add, contentDescription = "Log Activity")
                }
            }
        },
    ) { padding ->
        val s = snapshot
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                DashboardScreen(vm, s, onLog = { d -> nav.log(d) }, onOpenRun = { nav.navigate("run/$it") }, onTraining = { nav.navigate("training") })
            }
            composable("training") {
                TrainingScreen(s, onOpen = { nav.navigate("block/$it") }, onCreate = { nav.navigate("block/new") })
            }
            composable("runs") { RunsScreen(s, onOpen = { nav.navigate("run/$it") }, onLog = { nav.navigate("log") }) }
            composable("stats") { StatsScreen(s, onOpenRun = { nav.navigate("run/$it") }) }
            composable("settings") { SettingsScreen(vm, s) }
            composable("block/new") {
                BlockFormScreen(vm, s, null, onDone = { id -> nav.navigate("block/$id") { popUpTo("training") } }, onCancel = { nav.popBackStack() })
            }
            composable("block/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                BlockDetailScreen(
                    vm, s, id,
                    onEdit = { nav.navigate("block/$id/edit") },
                    onDeleted = { if (!nav.popBackStack("training", inclusive = false)) nav.popBackStack() },
                    onOpenRun = { nav.navigate("run/$it") },
                    onLog = { d -> nav.log(d) },
                )
            }
            composable("block/{id}/edit", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                BlockFormScreen(vm, s, e.arguments!!.getLong("id"), onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
            }
            composable(
                "log?date={date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { e ->
                val date = e.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ActivityFormScreen(vm, s, null, date,
                    onSaved = { id -> nav.navigate("run/$id") { popUpTo("log?date={date}") { inclusive = true } } },
                    onCancel = { nav.popBackStack() })
            }
            composable("run/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                RunDetailScreen(vm, s, id, onEdit = { nav.navigate("run/$id/edit") }, onDeleted = { nav.popBackStack() },
                    onOpenBlock = { nav.navigate("block/$it") })
            }
            composable("run/{id}/edit", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ActivityFormScreen(vm, s, e.arguments!!.getLong("id"), null, onSaved = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.log(date: LocalDate?) = navigate(if (date != null) "log?date=$date" else "log")
