package com.example.kmmbase.android.navigation

import kotlinx.serialization.Serializable

@Serializable
internal enum class StudioPageKey {
    ADD_ITEM, WARDROBE_PERMISSION, ITEM, OCCASION, GENERATING, SUGGESTIONS, OUTFIT, CAMERA_INTRO,
    TEACH, RECORDING, COMPILING, REVIEW, SAVED, SKILL, GUIDE, ASK, SETTINGS, COMPONENTS,
}

@Serializable
internal data class StudioRoute(val page: StudioPageKey, val id: String = "")
