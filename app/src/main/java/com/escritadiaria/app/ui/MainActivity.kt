package com.escritadiaria.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.escritadiaria.app.data.SettingsStore
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.escritadiaria.app.reminders.Notifs

class MainActivity : ComponentActivity() {
    private val startRequest = mutableStateOf<String?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) startRequest.value = intent?.getStringExtra(Notifs.EXTRA_START)
        if (Build.VERSION.SDK_INT >= 33 && !Notifs.canPost(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppTheme {
                val store = remember { SettingsStore(applicationContext) }
                val fontIndex by remember { store.flow.map { it.scriptFont } }.collectAsState(initial = 0)
                CompositionLocalProvider(LocalScriptFont provides ScriptFonts.get(fontIndex)) {
                    Box(Modifier.fillMaxSize().background(C.Ground)) {
                        AppNav(startRequest)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifs.EXTRA_START)?.let { startRequest.value = it }
    }
}

class SimpleFactory(private val builder: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
}

@Composable
fun AppNav(startRequest: MutableState<String?>) {
    val nav = rememberNavController()

    LaunchedEffect(startRequest.value) {
        val mode = startRequest.value ?: return@LaunchedEffect
        startRequest.value = null
        val current = nav.currentBackStackEntry?.destination?.route.orEmpty()
        if (!current.startsWith("session")) nav.navigate("session/$mode")
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onStart = { mode -> nav.navigate("session/$mode") },
                onNavigate = { route -> nav.navigate(route) },
            )
        }
        composable("session/{mode}") { entry ->
            val quick = entry.arguments?.getString("mode") == "quick"
            SessionScreen(
                quick = quick,
                onExit = { nav.popBackStack("home", inclusive = false) },
                onFinished = { id ->
                    nav.navigate("done/$id") { popUpTo("home") }
                },
            )
        }
        composable("done/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            DoneScreen(
                id = id,
                onHome = { nav.popBackStack("home", inclusive = false) },
                onWeek = { nav.navigate("week") { popUpTo("home") } },
            )
        }
        composable("reminders") {
            RemindersScreen(onBack = { nav.popBackStack() }, onEditQuestions = { nav.navigate("questions") })
        }
        composable("questions") { QuestionsScreen(onBack = { nav.popBackStack() }) }
        composable("week") {
            WeekScreen(onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("ritual/$id") })
        }
        composable("history") {
            HistoryScreen(onBack = { nav.popBackStack() }, onOpenPages = { id -> nav.navigate("ritual/$id") })
        }
        composable("ritual/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            RitualPagesScreen(id = id, onBack = { nav.popBackStack() })
        }
    }
}
