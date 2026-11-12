package com.example.kmmbase

import com.example.kmmbase.data.repository.shadow.HttpShadowRepository
import com.example.kmmbase.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.example.kmmbase.domain.usecase.builtinGuide
import kotlin.test.*

/**
 * Talks to the real SHADOW server. Opt-in because it needs network and the access code:
 *
 *     SHADOW_ACCESS_CODE=<code> ./gradlew :shared:testAndroidHostTest --tests '*LiveServerTest*'
 *
 * This is the check that the wiring matches the deployed contract — unit tests use fakes and
 * cannot catch a renamed field or a WebSocket that rejects the handshake.
 */
class LiveServerTest {
    private val code: String? = System.getenv("SHADOW_ACCESS_CODE")
    private val origin: String = System.getenv("SHADOW_ORIGIN") ?: DEFAULT_SHADOW_ORIGIN

    private fun repository() = HttpShadowRepository(origin, requireNotNull(code))

    @BeforeTest fun requireCode() {
        if (code.isNullOrBlank()) {
            println("SHADOW_ACCESS_CODE not set — skipping live server checks.")
        }
    }

    @Test fun healthSkillsAndWardrobeMatchTheDeployedContract() {
        if (code.isNullOrBlank()) return
        runBlocking {
            repository().use { repo ->
                val health = repo.health()
                assertTrue(health.mode.isNotBlank(), "health should report a mode")
                repo.authenticate(code)

                val skills = repo.skills()
                assertTrue(skills.isNotEmpty(), "the demo server should expose at least one skill")
                val lesson = repo.lesson(skills.first().id)
                assertTrue(lesson.steps.isNotEmpty(), "a skill should have steps")
                assertTrue(lesson.steps.all { it.instruction.isNotBlank() },
                    "every step needs an instruction for the HUD")

                val wardrobe = repo.wardrobe()
                assertTrue(wardrobe.isNotEmpty(), "the demo wardrobe should not be empty")
                assertTrue(wardrobe.any { it.imageUrl.startsWith("https://") },
                    "wardrobe items must resolve to absolute image URLs")
            }
        }
    }

    @Test fun guideSocketAcceptsTheAccessHeaderAndStartsASession() {
        if (code.isNullOrBlank()) return
        runBlocking {
            repository().use { repo ->
                val skillId = repo.skills().first().id
                repo.openSession().use { socket ->
                    val ready = withTimeout(20_000) {
                        socket.events.first { it is SessionEvent.Ready } as SessionEvent.Ready
                    }
                    assertTrue(ready.imageMaxSide in 128..1024,
                        "server should advertise a sane frame size, got ${ready.imageMaxSide}")

                    socket.start(skillId)
                    val hud = withTimeout(20_000) {
                        socket.events.first { it is SessionEvent.Hud && it.step != null } as SessionEvent.Hud
                    }
                    assertEquals(SessionPhase.GUIDING, hud.phase)
                    assertNotNull(hud.step).let { step ->
                        assertTrue(step.instruction.isNotBlank())
                        assertTrue(step.total > 0)
                    }
                    socket.stop()
                }
            }
        }
    }

    @Test fun stylistReturnsOutfitsWithWeatherAndImages() {
        if (code.isNullOrBlank()) return
        runBlocking {
            repository().use { repo ->
                val result = repo.suggest("Đám cưới", "", "Hà Nội")
                assertTrue(result.outfits.isNotEmpty(), "stylist should return outfits")
                assertTrue(result.source.isNotBlank())
                val outfit = result.outfits.first()
                assertTrue(outfit.reason.isNotBlank(), "each outfit needs a reason to read aloud")
                assertTrue(outfit.items.isNotEmpty())
                assertTrue(outfit.items.all { it.imageUrl.startsWith("https://") },
                    "outfit items must resolve to absolute image URLs")
            }
        }
    }

    /** Explicit opt-in: installs the same missing app-owned skill as the app's Start button. */
    @Test fun giftGuidePublishesFirstStepAndSpeech() {
        if (System.getenv("SHADOW_VERIFY_GIFT") != "1") return
        runBlocking {
            HttpShadowRepository(origin, code ?: DEFAULT_SHADOW_ACCESS_CODE).use { repo ->
                val gift = builtinGuide("gift")
                if (repo.skills().none { it.id == gift.skillId }) repo.saveSkill(gift)
                repo.openSession().use { socket ->
                    val step = CompletableDeferred<SessionEvent.Hud>()
                    val speech = CompletableDeferred<SessionEvent.Speech>()
                    val observer = launch(start = CoroutineStart.UNDISPATCHED) {
                        socket.events.collect { event ->
                            if (event is SessionEvent.Hud && event.phase == SessionPhase.GUIDING && event.step != null) step.complete(event)
                            if (event is SessionEvent.Speech && event.kind == "enter") speech.complete(event)
                        }
                    }
                    try {
                        socket.start(gift.skillId)
                        withTimeout(20_000) {
                            val first = step.await()
                            val spoken = speech.await()
                            assertEquals(0, first.step?.index)
                            assertEquals(4, first.step?.total)
                            assertTrue(first.step?.instruction?.contains("hộp") == true)
                            assertTrue(spoken.text.isNotBlank())
                            println("GIFT OUTPUT: step=${first.step?.index}, total=${first.step?.total}, instruction=${first.step?.instruction}")
                            println("GIFT SPEECH: ${spoken.text}")
                        }
                    } finally {
                        socket.stop()
                        observer.cancel()
                    }
                }
            }
        }
    }
}
