package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.StylingLocation
import com.example.kmmbase.domain.model.WardrobeItem
import com.example.kmmbase.domain.model.ShadowOutfit
data class ConversationTurn(val role: String, val text: String)
data class ConversationLink(val route: String, val label: String, val id: String = "")
sealed interface ConversationEvent {
    data class Text(val text: String) : ConversationEvent
    data class Action(val link: ConversationLink) : ConversationEvent
    data class Wardrobe(val items: List<WardrobeItem>, val total: Int, val gender: String) : ConversationEvent
    data class Outfits(val outfits: List<ShadowOutfit>) : ConversationEvent
    data object Done : ConversationEvent
}

/** A native chat request; credentials and transport stay in the data adapter. */
interface ConversationRepository : AutoCloseable {
    suspend fun converse(text: String, history: List<ConversationTurn>, image: String?, location: StylingLocation?, onEvent: (ConversationEvent) -> Unit)
}
