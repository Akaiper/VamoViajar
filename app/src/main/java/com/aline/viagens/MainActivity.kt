package com.aline.viagens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aline.viagens.data.Repo
import com.aline.viagens.data.UserPrefs
import com.aline.viagens.ui.LoginScreen
import com.aline.viagens.ui.SettingsScreen
import com.aline.viagens.ui.TripDetailScreen
import com.aline.viagens.ui.TripFormScreen
import com.aline.viagens.ui.TripsScreen
import com.aline.viagens.ui.VamoTheme
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
private fun App() {
    val user by remember { Repo.authState() }.collectAsStateWithLifecycle(initialValue = Repo.currentUser)
    val uid = user?.uid
    val prefs by remember(uid) { uid?.let { Repo.prefs(it) } ?: flowOf(Repo.cachedPrefs()) }
        .collectAsStateWithLifecycle(initialValue = Repo.cachedPrefs())

    VamoTheme(prefs) {
        if (uid == null) LoginScreen() else MainNav(uid, prefs)
    }
}

/** Volta uma tela, mas só se a tela atual for a esperada (evita voltar duas vezes). */
private fun NavHostController.backFrom(routePrefix: String) {
    if (currentBackStackEntry?.destination?.route?.startsWith(routePrefix) == true) popBackStack()
}

@Composable
private fun MainNav(uid: String, prefs: UserPrefs) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "trips") {
        composable("trips") {
            TripsScreen(
                uid = uid,
                onOpen = { id -> nav.navigate("trip/$id") },
                onNew = { nav.navigate("new") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("trip/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            TripDetailScreen(
                tripId = id,
                uid = uid,
                onBack = { nav.backFrom("trip/") },
                onEdit = { nav.navigate("edit/$id") },
            )
        }
        composable("new") {
            TripFormScreen(
                tripId = null,
                onBack = { nav.backFrom("new") },
                onSaved = { id ->
                    nav.navigate("trip/$id") { popUpTo("trips") }
                },
            )
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            TripFormScreen(
                tripId = id,
                onBack = { nav.backFrom("edit/") },
                onSaved = { nav.backFrom("edit/") },
            )
        }
        composable("settings") {
            SettingsScreen(uid = uid, prefs = prefs, onBack = { nav.backFrom("settings") })
        }
    }
}
