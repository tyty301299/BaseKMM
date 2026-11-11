package com.example.kmmbase.android.navigation

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.example.kmmbase.android.common.rememberStore
import com.example.kmmbase.android.features.home.HomeScreen
import com.example.kmmbase.android.features.notes.NoteEditScreen
import com.example.kmmbase.android.features.notes.NoteListScreen
import com.example.kmmbase.android.features.looks.screens.*
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.di.AppContainer
import com.example.kmmbase.presentation.common.UiMessage
import com.example.kmmbase.presentation.looks.LookAction
import com.example.kmmbase.presentation.studio.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable

@Serializable internal data object CameraRoute
@Serializable internal data object HistoryRoute
@Serializable internal data class ServiceRoute(val initialTab: Int, val settings: Boolean = false)
@Serializable internal data class LookDetailRoute(val id: String)
@Serializable internal data object HomeRoute
@Serializable internal data object NoteListRoute
@Serializable internal data class NoteEditRoute(val noteId: String? = null)

/** All routes, one-shot navigation effects and feedback collectors are owned here. */
@Composable
internal fun AppNavHost(container: AppContainer) {
    val language = LocalAppLanguage.current
    val nav = rememberNavController()
    val remote = rememberStore(key = "shadow-remote") { container.shadowStore() }
    val remoteState by remote.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val looks = rememberStore(key = "shadow-looks") { container.lookStore() }
    val studio = rememberStore(key = "shadow-studio") { container.studioStore() }
    val state by studio.state.collectAsState()
    val lookState by looks.state.collectAsState()
    var homeSelection by rememberSaveable { mutableStateOf(0) }
    val feedback = remember { Channel<UiMessage>(Channel.BUFFERED) }
    val snackbars = remember { SnackbarHostState() }
    var toastError by remember { mutableStateOf(false) }
    DisposableEffect(feedback) { onDispose { feedback.close() } }
    LaunchedEffect(looks) { looks.messages.collect { feedback.send(it) } }
    LaunchedEffect(studio) { studio.messages.collect { feedback.send(it) } }
    LaunchedEffect(remote) { remote.messages.collect { feedback.send(it) } }
    LaunchedEffect(feedback) {
        for (message in feedback) {
            toastError = message.isError
            snackbars.showSnackbar(language.systemText(message.text), withDismissAction = true, duration = SnackbarDuration.Short)
        }
    }
    LaunchedEffect(studio, nav) {
        studio.effects.collect { effect ->
            when (effect) {
                is StudioEffect.ItemAdded -> nav.navigate(StudioRoute(StudioPageKey.ITEM, effect.id))
                is StudioEffect.SkillSaved -> nav.navigate(StudioRoute(StudioPageKey.SAVED, effect.id))
                StudioEffect.ItemDeleted, StudioEffect.SkillDeleted -> nav.popBackStack()
                StudioEffect.DataCleared -> looks.dispatch(LookAction.Retake)
            }
        }
    }
    val go: (StudioPageKey) -> Unit = { nav.navigate(StudioRoute(it)) }
    val item: (String) -> Unit = { studio.dispatch(StudioAction.OpenItem(it)); nav.navigate(StudioRoute(StudioPageKey.ITEM, it)) }
    val skill: (String) -> Unit = { nav.navigate(StudioRoute(StudioPageKey.SKILL, it)) }
    val service: (Int) -> Unit = { nav.navigate(ServiceRoute(it)) }
    LaunchedEffect(remote) {
        val preferences = context.getSharedPreferences("shadow", android.content.Context.MODE_PRIVATE)
        val endpoint = preferences.getString("endpoint", null) ?: com.example.kmmbase.domain.model.DEFAULT_SHADOW_ORIGIN
        val code = if (endpoint == com.example.kmmbase.domain.model.DEFAULT_SHADOW_ORIGIN) com.example.kmmbase.domain.model.DEFAULT_SHADOW_ACCESS_CODE else ""
        remote.dispatch(com.example.kmmbase.presentation.shadow.ShadowAction.Connect(endpoint, code))
    }

    val openCamera: () -> Unit = {
        if (!looks.currentState.saving && studio.currentState.busy.isEmpty()) {
            looks.dispatch(LookAction.Retake); nav.navigate(CameraRoute)
        }
    }
    val home: (Int) -> Unit = { homeSelection = it; nav.popBackStack<HomeRoute>(inclusive = false) }
    CompositionLocalProvider(LocalLookDark provides state.data.settings.dark, LocalLookGlass provides state.data.settings.glass) {
        LookTheme {
            Box(Modifier.fillMaxSize()) {
                NavHost(navController = nav, startDestination = HomeRoute) {
                    composable<HomeRoute> {
                        LookHomeScreen(looks, studio, remoteState, homeSelection, { go(StudioPageKey.CAMERA_INTRO) },
                            { nav.navigate(LookDetailRoute(it)) }, service,
                            { go(StudioPageKey.ADD_ITEM) }, item, { go(StudioPageKey.OCCASION) },
                            { studio.dispatch(StudioAction.NewLesson); go(StudioPageKey.TEACH) }, skill,
                            { if (!lookState.saving && state.busy.isEmpty()) go(StudioPageKey.SETTINGS) },
                            { remote.dispatch(com.example.kmmbase.presentation.shadow.ShadowAction.Reconnect) })
                    }
                    composable<StudioRoute> { entry ->
                        StudioDestination(entry.toRoute(), studio, looks, remote, { nav.navigate(it) }, { nav.popBackStack() },
                            openCamera, home, service, { nav.navigate(HistoryRoute) },
                            { nav.navigate(ServiceRoute(0, settings = true)) })
                    }
                    composable<CameraRoute> {
                        CompositionLocalProvider(LocalLookDark provides true) {
                            LookCameraScreen(looks, { nav.popBackStack() }, {
                                nav.navigate(HistoryRoute) { popUpTo<CameraRoute> { inclusive = true } }
                            })
                        }
                    }
                    composable<HistoryRoute> {
                        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                            TextButton(onClick = { nav.popBackStack() }) { Text(language.text(R.string.back)) }
                            LookHistoryScreen(looks, studio, { nav.navigate(LookDetailRoute(it)) }, { go(StudioPageKey.CAMERA_INTRO) }, skill)
                        }
                    }
                    composable<LookDetailRoute> { entry ->
                        LookDetailScreen(looks, entry.toRoute<LookDetailRoute>().id, { nav.popBackStack() }, {
                            nav.navigate(CameraRoute) { popUpTo<LookDetailRoute> { inclusive = true } }
                        })
                    }
                    composable<ServiceRoute> { entry ->
                        HomeScreen(container = container, store = remote,
                            initialTab = entry.toRoute<ServiceRoute>().initialTab,
                            initialSettings = entry.toRoute<ServiceRoute>().settings)
                    }
                    composable<NoteListRoute> {
                        NoteListScreen(rememberStore { container.noteListStore() }, { nav.navigate(NoteEditRoute(it)) }, { nav.navigateUp() })
                    }
                    composable<NoteEditRoute> { entry ->
                        NoteEditScreen(rememberStore { container.noteEditStore(entry.toRoute<NoteEditRoute>().noteId) }) { nav.navigateUp() }
                    }
                }
                SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 88.dp)) { data ->
                    Snackbar(data, containerColor = if (toastError) MaterialTheme.colorScheme.errorContainer else LookPanel,
                        contentColor = if (toastError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.lookGlass())
                }
                BackHandler(enabled = state.busy.isNotEmpty() || lookState.saving) {}
            }
        }
    }
}
