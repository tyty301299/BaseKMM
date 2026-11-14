package com.example.kmmbase

import com.example.kmmbase.data.dto.shadow.*
import com.example.kmmbase.data.mapper.shadow.*
import com.example.kmmbase.domain.model.*
import com.example.kmmbase.domain.usecase.builtinGuide
import kotlinx.serialization.json.*
import kotlin.test.*

class ShadowDataContractTest {
    @Test fun editingMatchesStableIdsAndPreservesProviderMetadata() {
        val original = """{"id":"tea","provider_extension":{"version":9},"steps":[
            {"id":"a","instruction":"Old A","on_enter_speech":"Voice A","detector_key":"d1","next_step_id":"b"},
            {"id":"b","instruction":"Old B","on_enter_speech":"Custom voice B","detector_key":"d2"}]}"""
        // UI order differs from server order. An index-based patch would edit the wrong step.
        val draft = TeachDraft("tea", "Tea", listOf(
            ShadowStep("b", "Old B", "", ""), ShadowStep("a", "New A", "", ""),
        ), original)
        val wire = Json.parseToJsonElement(draft.toDto().raw).jsonObject
        val steps = wire.getValue("steps").jsonArray
        assertEquals("New A", steps[0].jsonObject.getValue("instruction").jsonPrimitive.content)
        assertEquals("New A", steps[0].jsonObject.getValue("on_enter_speech").jsonPrimitive.content)
        assertEquals("Custom voice B", steps[1].jsonObject.getValue("on_enter_speech").jsonPrimitive.content)
        assertEquals("d1", steps[0].jsonObject.getValue("detector_key").jsonPrimitive.content)
        assertEquals("b", steps[0].jsonObject.getValue("next_step_id").jsonPrimitive.content)
        assertEquals(9, wire.getValue("provider_extension").jsonObject.getValue("version").jsonPrimitive.int)
        assertEquals(original, draft.raw)
    }

    @Test fun builtinDefinitionsSerializeCompleteDetectorChains() {
        for (kind in listOf("fold", "pants", "gift")) {
            val draft = builtinGuide(kind)
            assertEquals(4, draft.steps.size)
            val wire = Json.parseToJsonElement(draft.toDto().raw).jsonObject
            val steps = wire.getValue("steps").jsonArray
            val detectors = wire.getValue("detectors").jsonObject
            steps.forEachIndexed { index, row ->
                val step = row.jsonObject
                val id = "step_${index + 1}"
                assertEquals(id, step.getValue("detector_key").jsonPrimitive.content)
                assertTrue(detectors.getValue(id).jsonObject.getValue("prompt").jsonPrimitive.content.contains(draft.detectorPrompts[index]))
                assertEquals(draft.steps[index].instruction, step.getValue("on_enter_speech").jsonPrimitive.content)
                if (index < 3) assertEquals("step_${index + 2}", step.getValue("next_step_id").jsonPrimitive.content)
                else assertNull(step["next_step_id"])
            }
        }
    }

    @Test fun socketMappingPreservesSpeechEpochAndRelativeMedia() {
        val dto = assertNotNull(decodeSessionEvent("""{"type":"hud.state","phase":"GUIDING","speech_epoch":42,
            "step":{"id":"s","reference_image_url":"/media/step.jpg"},"extra_future_field":true}"""))
        val event = dto.toDomain { "https://example.test$it" } as SessionEvent.Hud
        assertEquals(42L, event.speechEpoch)
        assertEquals("https://example.test/media/step.jpg", event.step?.referenceImageUrl)
        val speech = assertNotNull(decodeSessionEvent("""{"type":"speech","text":"Tiếp theo","epoch":43}"""))
            .toDomain { it } as SessionEvent.Speech
        assertEquals(43L, speech.epoch)
        assertEquals("Tiếp theo", speech.text)
    }

    @Test fun socketDefaultsAndUnknownMessagesRemainCompatible() {
        val ready = assertNotNull(decodeSessionEvent("""{"type":"session.ready","vision":{"image_max_side":0}}"""))
            .toDomain { it } as SessionEvent.Ready
        assertEquals(448, ready.imageMaxSide)
        assertNull(decodeSessionEvent("""{"type":"future.event"}"""))
        val event = assertNotNull(decodeSessionEvent("""{"type":"vision.result","error":"provider failed"}"""))
            .toDomain { it }
        assertIs<SessionEvent.Failure>(event)
    }

    @Test fun absentOptionalFieldsAndMixedRuleShapesDecode() {
        val row = Json.parseToJsonElement("""{"id":"shirt","name":"Áo","colors":["white",{"text":"blue"}]}""").jsonObject
        val item = row.toWardrobeItem { it }.toDomain()
        assertEquals(listOf("white", "blue"), item.colors)
        assertFalse(item.demo)
        assertEquals("unknown", item.gender)
        assertEquals("women", Json.parseToJsonElement("""{"id":"shirt","name":"Áo","gender":"women"}""").jsonObject.toWardrobeItem { it }.toDomain().gender)
        assertEquals("", item.imageUrl)
        assertFailsWith<IllegalArgumentException> {
            Json.parseToJsonElement("""{"name":"Missing ID"}""").jsonObject.toWardrobeItem { it }
        }
    }
}
