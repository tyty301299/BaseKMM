package com.example.kmmbase.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.kmmbase.android.common.rememberStore
import com.example.kmmbase.android.BuildConfig
import com.example.kmmbase.android.features.home.HomeScreen
import com.example.kmmbase.android.features.kitchen.AndroidKitchenDevice
import com.example.kmmbase.android.features.kitchen.KitchenFeature
import com.example.kmmbase.android.features.kitchen.KitchenScreen
import com.example.kmmbase.android.features.notes.NoteEditScreen
import com.example.kmmbase.android.features.notes.NoteListScreen
import com.example.kmmbase.di.AppContainer
import kotlinx.serialization.Serializable

@Serializable internal data object HomeRoute
@Serializable internal data object NoteListRoute
@Serializable internal data object KitchenRoute
@Serializable internal data class NoteEditRoute(val noteId: String? = null)

/** The only place that decides where each screen goes. */
@Composable
internal fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                greeting = container.getGreeting::execute,
                onOpenNotes = { nav.navigate(NoteListRoute) },
                onOpenKitchen = { nav.navigate(KitchenRoute) },
            )
        }
        composable<KitchenRoute> {
            val appContext = LocalContext.current.applicationContext
            KitchenScreen(
                feature = rememberStore {
                    val device = AndroidKitchenDevice(appContext)
                    KitchenFeature(device, container.kitchenStore(device, BuildConfig.KITCHEN_SERVER_URL))
                },
                onBack = { nav.navigateUp() },
            )
        }
        composable<NoteListRoute> {
            NoteListScreen(
                store = rememberStore { container.noteListStore() },
                onOpenNote = { id -> nav.navigate(NoteEditRoute(id)) },
                onBack = { nav.navigateUp() },
            )
        }
        composable<NoteEditRoute> { entry ->
            val noteId = entry.toRoute<NoteEditRoute>().noteId
            NoteEditScreen(
                store = rememberStore { container.noteEditStore(noteId) },
                onDone = { nav.navigateUp() },
            )
        }
    }
}
