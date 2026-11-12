package com.example.kmmbase.android.navigation

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.runtime.*
import com.example.kmmbase.android.features.studio.presentation.screens.*
import com.example.kmmbase.android.features.studio.presentation.components.StudioPage
import com.example.kmmbase.android.features.looks.presentation.screens.LookOutfitContent
import com.example.kmmbase.presentation.looks.*
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioDestination(route: StudioRoute, studio: StudioStore, looks: LookStore, remote: com.example.kmmbase.presentation.shadow.ShadowStore,
    navigate: (StudioRoute) -> Unit, onBack: () -> Unit, openCamera: () -> Unit,
    homeTab: (Int) -> Unit, openService: (Int) -> Unit, openHistory: () -> Unit, openConnectionSettings: () -> Unit) {
    val language = LocalAppLanguage.current
    LaunchedEffect(route.page, route.id) {
        if (route.page in setOf(StudioPageKey.GUIDE, StudioPageKey.ASK) &&
            studio.currentState.activeSkillId != route.id) studio.dispatch(StudioAction.OpenSkill(route.id))
    }
    val lookState by looks.state.collectAsState()
    val go: (StudioPageKey) -> Unit = { navigate(StudioRoute(it)) }
    when (route.page) {
        StudioPageKey.ADD_ITEM -> StudioAddItemScreen(studio, onBack) { go(StudioPageKey.WARDROBE_PERMISSION) }
        StudioPageKey.WARDROBE_PERMISSION -> StudioCameraIntroScreen(studio, true, onBack) {}
        StudioPageKey.ITEM -> StudioItemScreen(studio, route.id, onBack) { preset ->
            looks.dispatch(LookAction.SelectPreset(preset)); go(StudioPageKey.CAMERA_INTRO)
        }
        StudioPageKey.OCCASION -> StudioOccasionScreen(studio, looks, onBack, {
            studio.dispatch(StudioAction.Suggest(looks.currentState.occasion)); go(StudioPageKey.GENERATING)
        }, { go(StudioPageKey.ADD_ITEM) })
        StudioPageKey.GENERATING -> StudioGeneratingScreen(studio, onBack, { go(StudioPageKey.SUGGESTIONS) }, {
            studio.dispatch(StudioAction.Suggest(looks.currentState.occasion))
        })
        StudioPageKey.SUGGESTIONS -> StudioSuggestionsScreen(studio, onBack, { preset ->
            looks.dispatch(LookAction.SelectPreset(preset)); go(StudioPageKey.OUTFIT)
        }, { go(StudioPageKey.ADD_ITEM) })
        StudioPageKey.OUTFIT -> StudioPage(language.text(R.string.ui_029), studio, onBack) {
            LookOutfitContent(lookState, looks, { go(StudioPageKey.CAMERA_INTRO) }, { openService(1) })
        }
        StudioPageKey.CAMERA_INTRO -> StudioCameraIntroScreen(studio, false, onBack, openCamera)
        StudioPageKey.TEACH -> StudioTeachScreen(studio, onBack) {
            studio.dispatch(StudioAction.StartRecording); go(StudioPageKey.RECORDING)
        }
        StudioPageKey.RECORDING -> StudioRecordingScreen(studio, onBack) {
            studio.dispatch(StudioAction.CompileSample); go(StudioPageKey.COMPILING)
        }
        StudioPageKey.COMPILING -> StudioCompileScreen(studio, onBack) { go(StudioPageKey.REVIEW) }
        StudioPageKey.REVIEW -> StudioReviewScreen(studio, onBack)
        StudioPageKey.SAVED -> StudioSavedSkillScreen(studio, route.id, onBack, {
            studio.dispatch(StudioAction.OpenSkill(route.id)); navigate(StudioRoute(StudioPageKey.GUIDE, route.id))
        }, openHistory)
        StudioPageKey.SKILL -> StudioSkillDetailScreen(studio, route.id, onBack) {
            studio.dispatch(StudioAction.OpenSkill(route.id)); navigate(StudioRoute(StudioPageKey.GUIDE, route.id))
        }
        StudioPageKey.GUIDE -> StudioGuideScreen(studio, onBack, { navigate(StudioRoute(StudioPageKey.ASK, route.id)) }, { homeTab(2) })
        StudioPageKey.ASK -> StudioAskScreen(studio, onBack) { openService(0) }
        StudioPageKey.SETTINGS -> StudioSettingsScreen(studio, remote, onBack, openConnectionSettings, { go(StudioPageKey.COMPONENTS) })
        StudioPageKey.COMPONENTS -> StudioComponentsScreen(studio, onBack) { go(StudioPageKey.CAMERA_INTRO) }
    }
}
