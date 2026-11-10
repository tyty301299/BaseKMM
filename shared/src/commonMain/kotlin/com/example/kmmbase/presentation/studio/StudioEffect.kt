package com.example.kmmbase.presentation.studio

sealed interface StudioEffect {
    data class ItemAdded(val id: String) : StudioEffect
    data class SkillSaved(val id: String) : StudioEffect
    data object ItemDeleted : StudioEffect
    data object SkillDeleted : StudioEffect
    data object DataCleared : StudioEffect
}
