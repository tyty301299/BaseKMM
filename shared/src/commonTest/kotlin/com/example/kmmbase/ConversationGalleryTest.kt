package com.example.kmmbase

import com.example.kmmbase.data.repository.conversation.decodeGalleryEvent
import com.example.kmmbase.domain.repository.ConversationEvent
import kotlin.test.*

class ConversationGalleryTest {
    @Test fun wardrobeKeepsActualImagesAndGenderFromTheServer() {
        val event = assertIs<ConversationEvent.Wardrobe>(decodeGalleryEvent("""{"type":"wardrobe","total":8,"gender_filter":"women","items":[{"id":"shirt","name":"Áo trắng","category":"top","gender":"women","image_url":"/wardrobe-media/shirt.jpg"}]}""", "https://ski.test"))
        assertEquals(8, event.total)
        assertEquals("women", event.gender)
        assertEquals("https://ski.test/wardrobe-media/shirt.jpg", event.items.single().imageUrl)
        assertEquals("women", event.items.single().gender)
    }
    @Test fun outfitAndEmptyWardrobeStillProduceVisibleUiEvents() {
        val event = assertIs<ConversationEvent.Outfits>(decodeGalleryEvent("""{"type":"outfits","outfits":[{"title":"Đi chơi","reason":"Thoải mái","items":[{"id":"shirt","name":"Áo","image_url":"javascript:bad"}]}]}""", "https://ski.test"))
        assertEquals("Đi chơi", event.outfits.single().title)
        assertEquals("", event.outfits.single().items.single().imageUrl)
        val empty = assertIs<ConversationEvent.Wardrobe>(decodeGalleryEvent("""{"type":"wardrobe","items":[],"total":0}""", "https://ski.test"))
        assertTrue(empty.items.isEmpty())
    }
}
