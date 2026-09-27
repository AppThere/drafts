package com.appthere.drafts.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Caret movement across block boundaries, driven through the keyboard.
 *
 * `CaretTest` in `:editor-engine` proves the engine puts the caret in the right place. This proves
 * the keys are wired to it, which is a separate thing and the half that actually fails in practice:
 * an arrow key that the field swallows never reaches the engine at all, and the caret simply stops
 * at the block boundary with no error anywhere.
 *
 * Each test asserts on the caret *and* on what is rendered, because either alone can be right while
 * the other is wrong -- a caret that moved with no field following it is just as broken as a field
 * that revealed without the caret.
 */
@OptIn(ExperimentalTestApi::class)
class CaretNavigationTest {
    @Test
    fun `down arrow on the last line moves into the block below`() =
        runComposeUiTest {
            // Clicked rather than placed: Down leaves the block from anywhere on the last line, so
            // where in the line the click lands does not matter here.
            val state = showDocument(DOCUMENT)
            onNodeWithText(BRAVO_PREVIEW).performClick()

            onNodeWithText(BRAVO_REVEAL).performKeyInput { pressKey(Key.DirectionDown) }

            assertEquals(Caret(state.blocks[2].id, 0), state.caret)
            onNodeWithText(BRAVO_PREVIEW).assertIsDisplayed()
        }

    @Test
    fun `up arrow on the first line moves into the end of the block above`() =
        runComposeUiTest {
            val state = showDocument(DOCUMENT)
            onNodeWithText(BRAVO_PREVIEW).performClick()

            onNodeWithText(BRAVO_REVEAL).performKeyInput { pressKey(Key.DirectionUp) }

            assertEquals(Caret(state.blocks[0].id, ALPHA.length), state.caret)
        }

    @Test
    fun `left arrow at offset zero leaves the block backwards`() =
        runComposeUiTest {
            val state = showDocument(DOCUMENT)
            state.place(Caret(state.blocks[1].id, 0))
            waitForIdle()

            onNodeWithText(BRAVO_REVEAL).performKeyInput { pressKey(Key.DirectionLeft) }

            assertEquals(Caret(state.blocks[0].id, ALPHA.length), state.caret)
        }

    @Test
    fun `right arrow at the end leaves the block forwards`() =
        runComposeUiTest {
            val state = showDocument(DOCUMENT)
            state.place(Caret(state.blocks[1].id, BRAVO_REVEAL.length))
            waitForIdle()

            onNodeWithText(BRAVO_REVEAL).performKeyInput { pressKey(Key.DirectionRight) }

            assertEquals(Caret(state.blocks[2].id, 0), state.caret)
        }

    @Test
    fun `an arrow key in the middle of a block is left to the field`() =
        runComposeUiTest {
            // The guard on all of the above. If the editor consumed every arrow key, moving within a
            // paragraph would jump to the next block, and each test above would still pass.
            val state = showDocument(DOCUMENT)
            state.place(Caret(state.blocks[1].id, 2))
            waitForIdle()

            onNodeWithText(BRAVO_REVEAL).performKeyInput { pressKey(Key.DirectionRight) }

            assertEquals(state.blocks[1].id, state.caret?.block, "The caret left the block")
        }

    @Test
    fun `Enter splits the block and the caret follows into the new one`() =
        runComposeUiTest {
            val state = showDocument(DOCUMENT)
            state.place(Caret(state.blocks[1].id, "Bravo ".length))
            waitForIdle()

            onNodeWithText(BRAVO_REVEAL).performKeyInput { pressKey(Key.Enter) }

            assertEquals(Caret(state.blocks[2].id, 0), state.caret)
            onNodeWithText("*two*.").assertIsDisplayed()
        }

    @Test
    fun `Backspace at offset zero merges into the block above`() =
        runComposeUiTest {
            val state = showDocument(DOCUMENT)
            state.place(Caret(state.blocks[1].id, 0))
            waitForIdle()

            onNodeWithText(BRAVO_REVEAL).performKeyInput { pressKey(Key.Backspace) }

            assertEquals(Caret(state.blocks[0].id, ALPHA.length), state.caret)
            onNodeWithText(ALPHA + BRAVO_REVEAL).assertIsDisplayed()
        }

    @Test
    fun `Backspace at the very start of the document does nothing`() =
        runComposeUiTest {
            val state = showDocument(DOCUMENT)
            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            onNodeWithText(ALPHA).performKeyInput { pressKey(Key.Backspace) }

            assertEquals(3, state.blocks.size)
        }

    @Test
    fun `typing leaves the caret after what was typed`() =
        runComposeUiTest {
            // A regression test with a specific history. The field used to hold its own
            // `TextFieldValue` remembered against the block, and a keystroke produces a new block
            // object -- so the value was rebuilt from scratch and the caret snapped to offset 0
            // after every character typed.
            val state = showDocument(DOCUMENT)
            state.place(Caret(state.blocks[1].id, "Bravo".length))
            waitForIdle()

            onNodeWithText(BRAVO_REVEAL).performTextInput("!")

            assertEquals("Bravo! *two*.", state.sourceOf(state.blocks[1].block))
            assertEquals("Bravo!".length, state.caret?.offset)
        }

    private fun ComposeUiTest.showDocument(text: String): EditorState {
        val state = EditorState(DocumentSession(text))
        setContent { BlockEditor(state = state) }
        return state
    }

    private companion object {
        const val ALPHA = "Alpha one."
        const val BRAVO_PREVIEW = "Bravo two."
        const val BRAVO_REVEAL = "Bravo *two*."
        const val CHARLIE = "Charlie three."

        const val DOCUMENT = "$ALPHA\n\n$BRAVO_REVEAL\n\n$CHARLIE\n"
    }
}
