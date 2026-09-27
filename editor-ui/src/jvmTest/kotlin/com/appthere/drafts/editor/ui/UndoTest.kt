package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.engine.sourceOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Undo as the user reaches it: through the keyboard, after typing.
 *
 * `UndoHistoryTest` proves the stack. This proves the wiring -- that the editor's own edits are the
 * ones being recorded, which is the part that silently rots. An edit path added later that calls
 * the session directly would leave no trace in the history, and nothing but a test at this level
 * would notice.
 */
@OptIn(ExperimentalTestApi::class)
class UndoTest {
    @Test
    fun `ctrl-Z undoes what was typed`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show()
            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            onNodeWithText(FIRST).performTextInput("New ")
            assertEquals("New $FIRST", state.sourceOfFirst())

            pressUndo()

            assertEquals(FIRST, state.sourceOfFirst(), "Ctrl+Z left the typing in place")
        }

    @Test
    fun `a typed word undoes in one press`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // The behaviour that decides whether undo is usable. `performTextInput` sends the text
            // a character at a time, which is exactly the case coalescing exists for.
            val state = show()
            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            onNodeWithText(FIRST).performTextInput("Hello")

            pressUndo()

            assertEquals(FIRST, state.sourceOfFirst(), "One press should undo the whole word")
        }

    @Test
    fun `ctrl-shift-Z redoes it`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show()
            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            onNodeWithText(FIRST).performTextInput("New ")
            pressUndo()
            pressRedo()

            assertEquals("New $FIRST", state.sourceOfFirst())
        }

    @Test
    fun `undo puts back a block that Enter split in two`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show()
            state.place(Caret(state.blocks[0].id, "First".length))
            waitForIdle()

            onNodeWithText(FIRST).performKeyInput { pressKey(Key.Enter) }
            assertEquals(THREE_BLOCKS + 1, state.blocks.size, "Enter did not split the block")

            pressUndo()

            assertEquals(THREE_BLOCKS, state.blocks.size, "Undo did not put the block back together")
            assertEquals(FIRST, state.sourceOfFirst())
        }

    @Test
    fun `undo puts back a selection that was deleted`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show()

            onNodeWithText(FIRST).performMouseInput {
                moveTo(Offset(0f, 2f))
                press()
                release()
            }
            state.selectAll()
            waitForIdle()
            state.deleteSelection()
            waitForIdle()

            pressUndo()

            assertEquals(FIRST, state.sourceOfFirst())
            assertEquals(THREE_BLOCKS, state.blocks.size)
        }

    @Test
    fun `typing one character at a time still undoes as one word`() {
        // The path the running app takes, simulated exactly: a text field reports its whole
        // contents after every keystroke, and `EditorState.replace` is handed all of it each time.
        // Recorded as written, that is six replacements of the entire block and undo steps back
        // through them one by one.
        val state = EditorState(DocumentSession("$FIRST\n"))
        state.place(Caret(state.blocks[0].id, 0))

        var contents = FIRST
        "Hello".forEachIndexed { index, character ->
            contents = contents.substring(0, index) + character + contents.substring(index)
            state.replace(state.blocks[0].block.source!!, contents, index + 1)
        }
        assertEquals("Hello$FIRST", state.sourceOfFirst())

        state.undo()

        assertEquals(FIRST, state.sourceOfFirst(), "Undo went back a character at a time")
    }

    private fun SkikoComposeUiTest.pressUndo() {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.pressRedo() {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.Z)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    private fun EditorState.sourceOfFirst(): String = sourceOf(blocks.first().block)

    private fun SkikoComposeUiTest.show(): EditorState {
        val state = EditorState(DocumentSession("$FIRST\n\n$SECOND\n\n$LAST\n"))
        setContent { HostedEditor(state) }
        return state
    }

    private companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 900f

        const val FIRST = "First paragraph."
        const val SECOND = "Second paragraph."
        const val LAST = "A final paragraph."
        const val THREE_BLOCKS = 3
    }
}
