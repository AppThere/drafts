package com.appthere.drafts.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.MotionPreference
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 4.2's cross-fade.
 *
 * "Cross-fade inline decoration over 120ms with no layout animation. Because block metrics are
 * identical in both states (4.1), nothing moves -- only glyph styling changes. Respect
 * `prefers-reduced-motion`: at reduced motion the switch is instantaneous."
 *
 * Three claims, and the third is the one that matters most to the reader it is written for: at
 * reduced motion there is no fade at all. `Motion.revealMillis` had been in the design system since
 * Phase 3 and read nowhere, so the switch was instant for everybody -- which happened to satisfy
 * the third claim by failing the first.
 */
@OptIn(ExperimentalTestApi::class)
class RevealCrossFadeTest {
    @Test
    fun `the preview is still there part-way through the fade`() {
        // What a cross-fade is. Half a frame after the caret arrives both states are on screen,
        // one going and one coming; without the fade the preview would already be gone.
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(ReaderSettings(motion = MotionPreference.Full))
            mainClock.autoAdvance = false

            state.place(Caret(state.blocks.first().id, 0))
            mainClock.advanceTimeBy(HALF_A_FADE)

            // Two nodes with the same words: the preview on its way out and the field on its way
            // in, sharing the row. That count *is* the cross-fade -- one node would mean the
            // switch had already happened.
            assertEquals(BOTH, onAllNodesWithText(FIRST).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `reduced motion switches without a fade`() {
        // 4.2's own exception, and 10.2's "instant state changes". A reader who asked for less
        // motion gets the reveal immediately rather than over an eighth of a second.
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(ReaderSettings(motion = MotionPreference.Reduced))
            mainClock.autoAdvance = false

            state.place(Caret(state.blocks.first().id, 0))
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()

            // One node: the field has taken over outright rather than sharing the row with a
            // preview that is still fading.
            assertEquals(ONE, onAllNodesWithText(FIRST).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `nothing moves while it fades`() {
        // "no layout animation ... nothing moves". The reserved height is what makes that true, and
        // a fade that changed the row's height would move every block below it on every click.
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(ReaderSettings(motion = MotionPreference.Full))
            val before = onNodeWithText(SECOND).getBoundsInRoot()
            mainClock.autoAdvance = false

            state.place(Caret(state.blocks.first().id, 0))
            mainClock.advanceTimeBy(HALF_A_FADE)
            val midway = onNodeWithText(SECOND).getBoundsInRoot()

            assertEquals(before.top, midway.top, "The block below moved while the one above faded")
            onNodeWithText(SECOND).assertHeightIsEqualTo(before.bottom - before.top)
        }
    }

    private fun SkikoComposeUiTest.show(settings: ReaderSettings): EditorState {
        val state = EditorState(DocumentSession("$FIRST\n\n$SECOND\n"))
        setContent {
            DraftsTheme(settings) {
                Box(Modifier.fillMaxSize().background(settings.theme.paletteFor(false).background)) {
                    BlockEditor(state = state)
                }
            }
        }
        waitForIdle()
        return state
    }

    private companion object {
        val SIZE = Size(900f, 700f)
        const val FIRST = "The first paragraph."
        const val SECOND = "The second paragraph."

        /** Half of 4.2's 120ms, so the fade is demonstrably in progress. */
        const val HALF_A_FADE = 60L

        const val BOTH = 2
        const val ONE = 1
    }
}
