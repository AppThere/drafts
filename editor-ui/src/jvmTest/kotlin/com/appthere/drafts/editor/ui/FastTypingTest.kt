package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keys that arrive faster than the editor composes.
 *
 * A field reports each edit as its whole new text, and the edit was applied to the block as it
 * stood at the row's last composition. Two keys inside one frame -- fast typing, a slow frame, an
 * input method committing in pieces -- applied the second against a block that no longer looked like
 * that, and the text came out doubled: "ab" typed into an empty block became "aba". Enter then split
 * the stray letter into the next paragraph, where every line after it added another. Found on a
 * Chromebook, where frames take 20-30ms, 2026-10-03.
 *
 * The frame clock is held still here so that nothing recomposes between the keys -- which is the
 * whole of the bug, and what a fast machine running the test would otherwise hide.
 */
@OptIn(ExperimentalTestApi::class)
class FastTypingTest {
    @Test
    fun `two keys in one frame are typed once each`() =
        runSkikoComposeUiTest(size = SIZE) {
            val state = show("")
            mainClock.autoAdvance = false

            onNode(isFocused()).performTextInput("a")
            onNode(isFocused()).performTextInput("b")
            mainClock.advanceTimeByFrame()
            waitForIdle()

            assertEquals("ab", state.sourceOf(state.blocks.first().block))
        }

    @Test
    fun `a word typed in one frame and split leaves nothing stray in the next paragraph`() =
        // What the Chromebook showed: Enter split the doubled letters off into the next paragraph.
        // Typed in front of existing text, so that the split has a second paragraph to make.
        runSkikoComposeUiTest(size = SIZE) {
            val state = show("tail")
            mainClock.autoAdvance = false

            "Line".forEach { onNode(isFocused()).performTextInput(it.toString()) }
            onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
            mainClock.advanceTimeByFrame()
            waitForIdle()

            assertEquals(listOf("Line", "tail"), state.blocks.map { state.sourceOf(it.block) })
        }

    @Test
    fun `a new document written a line at a time is a paragraph a line`() =
        // The Chromebook run, exactly: a line, Enter, the next line, with nothing recomposing between
        // the keys. It used to come out as one paragraph with a tail of doubled letters below it --
        // Enter at the end of the document made no paragraph to type into (`RoomsTest`), and the
        // letters doubled into whatever was below the caret.
        runSkikoComposeUiTest(size = SIZE) {
            val state = show("")
            mainClock.autoAdvance = false

            listOf("Line 1", "Line 2", "Line 3").forEachIndexed { index, line ->
                if (index > 0) onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
                mainClock.advanceTimeByFrame()
                line.forEach { onNode(isFocused()).performTextInput(it.toString()) }
            }
            mainClock.advanceTimeByFrame()
            waitForIdle()

            assertEquals(listOf("Line 1", "Line 2", "Line 3"), state.blocks.map { state.sourceOf(it.block) })
        }

    @Test
    fun `backspace straight after a key does not merge the paragraph into the one above`() =
        // The key handler decided "Backspace at the start of a block merges it" from the caret as it
        // was at the last composition. A key and a Backspace in one frame saw offset 0 and merged
        // the block into the one above instead of deleting what was just typed.
        //
        // What it does not yet do is delete the key. Backspace that is not at a block's start is the
        // field's own, and this value-based field applies it to the value it was last composed with,
        // which a frame ago did not have the "x" -- so the Backspace is lost rather than misapplied.
        // Moving the field to `TextFieldState` would fix that; until then this asserts only what is
        // true, that nothing is merged and nothing the reader wrote is lost.
        runSkikoComposeUiTest(size = SIZE) {
            val state = show("Above\n\nBelow")
            state.place(Caret(state.blocks.last().id, 0))
            waitForIdle()
            mainClock.autoAdvance = false

            onNode(isFocused()).performTextInput("x")
            onNode(isFocused()).performKeyInput { pressKey(Key.Backspace) }
            mainClock.advanceTimeByFrame()
            waitForIdle()

            val sources = state.blocks.map { state.sourceOf(it.block) }
            assertEquals(2, sources.size, "Backspace merged the paragraphs: $sources")
            assertEquals("Above", sources.first())
            assertTrue(sources.last().endsWith("Below"), "The paragraph lost its text: $sources")
        }

    private fun SkikoComposeUiTest.show(text: String): EditorState {
        val state = EditorState(DocumentSession(text))
        setContent { DraftsTheme { HostedEditor(state) } }
        waitForIdle()
        state.place(Caret(state.blocks.first().id, 0))
        waitForIdle()
        return state
    }

    private companion object {
        val SIZE = Size(700f, 500f)
    }
}
