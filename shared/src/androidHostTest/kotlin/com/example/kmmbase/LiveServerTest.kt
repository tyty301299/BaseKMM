package com.example.kmmbase

import com.example.kmmbase.data.repository.HttpShadowRepository
import com.example.kmmbase.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
}
