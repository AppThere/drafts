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
import com.appthere.drafts.editor.engine.BlockParser
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Backspacing every word of a paragraph away leaves the caret in it, ready for the next word.
 *
 * Found on a Chromebook: the emptied paragraph vanished with the caret in it, and a document of one
 * paragraph could not be typed into again at all. Both kinds, because both went the same way.
 */
@OptIn(ExperimentalTestApi::class)
class EmptiedParagraphTest {
    @Test
    fun `a document's only paragraph backspaced away can be typed into again`() =
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(DocumentSession(WORD))

            erase(WORD)
            onNode(isFocused()).performTextInput(AGAIN)
            waitForIdle()

            assertEquals(AGAIN, state.text)
        }

    @Test
    fun `a screenplay's only line backspaced away can be typed into again`() =
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(DocumentSession(WORD, BlockParser.Fountain()))

            erase(WORD)
            onNode(isFocused()).performTextInput(AGAIN)
            waitForIdle()

            assertEquals(AGAIN, state.text)
        }

    @Test
    fun `a paragraph backspaced away in the middle is typed into where it was`() =
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(DocumentSession("Above\n\n$WORD\n\nBelow"), at = WORD)

            erase(WORD)
            onNode(isFocused()).performTextInput(AGAIN)
            waitForIdle()

            assertEquals("Above\n\n$AGAIN\n\nBelow", state.text)
        }

    private fun SkikoComposeUiTest.erase(word: String) {
        repeat(word.length) { onNode(isFocused()).performKeyInput { pressKey(Key.Backspace) } }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.show(
        session: DocumentSession,
        at: String = WORD,
    ): EditorState {
        val state = EditorState(session)
        setContent { DraftsTheme { HostedEditor(state) } }
        waitForIdle()
        val block = state.blocks.first { state.sourceOf(it.block) == at }
        state.place(Caret(block.id, at.length))
        waitForIdle()
        return state
    }

    private companion object {
        val SIZE = Size(700f, 500f)
        const val WORD = "Hello"
        const val AGAIN = "Again"
    }
}
