package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.StylingLocation
data class ConversationTurn(val role: String, val text: String)
data class ConversationLink(val route: String, val label: String, val id: String = "")
sealed interface ConversationEvent {
    data class Text(val text: String) : ConversationEvent
    data class Action(val link: ConversationLink) : ConversationEvent
    data object Done : ConversationEvent
}

/** A native chat request; credentials and transport stay in the data adapter. */
interface ConversationRepository : AutoCloseable {
    suspend fun converse(text: String, history: List<ConversationTurn>, image: String?, location: StylingLocation?, onEvent: (ConversationEvent) -> Unit)
}
