package com.appthere.drafts.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 12's auto-hide, wired to something that types.
 *
 * "**Chrome auto-hides.** On sustained typing, toolbars and rails fade out. Any pointer movement,
 * keypress of a modifier, or edge gesture brings them back."
 *
 * `ChromeVisibilityTest` proves the policy at explicit instants. What is left is whether anything
 * calls it -- a policy nothing drives would leave every unit test green and the chrome permanently
 * on screen.
 *
 * The clock is injected and driven by the test clock. With a real monotonic source, `delay` obeys
 * the virtual clock while the deadline arithmetic obeys the wall clock, the deadline never
 * arrives, and the test proves only that the two disagree. The snapshot effect learned this first.
 */
@OptIn(ExperimentalTestApi::class)
class AutoHideTest {
    @Test
    fun `sustained typing hides the chrome`() {
        runSkikoComposeUiTest(size = SIZE) {
            val visibility = ChromeVisibility()
            var hidden by mutableStateOf(false)
            var revision by mutableIntStateOf(0)
            setContent {
                ChromeEffect(visibility, revision, { hidden = it }, now = { mainClock.currentTime })
            }

            revision = 1
            mainClock.advanceTimeBy(ChromeVisibility.SUSTAINED_AFTER_MILLIS + FRAME)
            waitForIdle()

            assertTrue(hidden, "The chrome was still showing after sustained typing")
        }
    }

    @Test
    fun `a single keystroke does not`() {
        // Correcting a word must not rearrange the interface around the reader.
        runSkikoComposeUiTest(size = SIZE) {
            val visibility = ChromeVisibility()
            var hidden by mutableStateOf(false)
            var revision by mutableIntStateOf(0)
            setContent {
                ChromeEffect(visibility, revision, { hidden = it }, now = { mainClock.currentTime })
            }

            revision = 1
            mainClock.advanceTimeBy(ChromeVisibility.SUSTAINED_AFTER_MILLIS / 3)
            waitForIdle()

            assertFalse(hidden, "The chrome went after a single keystroke")
        }
    }

    @Test
    fun `a document nobody has touched keeps its chrome`() {
        // Revision zero is the document as opened. Treating it as typing would fade the chrome a
        // second and a half after every launch, before anyone had touched anything.
        runSkikoComposeUiTest(size = SIZE) {
            val visibility = ChromeVisibility()
            var hidden by mutableStateOf(false)
            setContent {
                ChromeEffect(visibility, revision = 0, onChange = { hidden = it }, now = { mainClock.currentTime })
            }

            mainClock.advanceTimeBy(ChromeVisibility.SUSTAINED_AFTER_MILLIS * 4)
            waitForIdle()

            assertFalse(hidden)
        }
    }

    @Test
    fun `being roused before the deadline keeps the chrome`() {
        // The reader reached for something mid-sentence. The pending hide must not land anyway a
        // moment later, which is what would happen if the effect reported its own decision without
        // asking the policy again.
        runSkikoComposeUiTest(size = SIZE) {
            val visibility = ChromeVisibility()
            var hidden by mutableStateOf(false)
            var revision by mutableIntStateOf(0)
            setContent {
                ChromeEffect(visibility, revision, { hidden = it }, now = { mainClock.currentTime })
            }

            revision = 1
            mainClock.advanceTimeBy(ChromeVisibility.SUSTAINED_AFTER_MILLIS / 2)
            visibility.roused()
            mainClock.advanceTimeBy(ChromeVisibility.SUSTAINED_AFTER_MILLIS)
            waitForIdle()

            assertFalse(hidden, "A hide landed after the reader had reached for the chrome")
        }
    }

    private companion object {
        val SIZE = Size(900f, 700f)
        const val FRAME = 32L
    }
}
