package com.appthere.drafts.editor.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A finger and a mouse want different things from the same drag.
 *
 * A mouse dragged down the document selects, and [CrossBlockSelectionTest] holds it to that. A
 * finger dragged down the document means scroll -- on a phone there is no other way through it --
 * and a touch that only taps has to place the caret the way a click does.
 */
@OptIn(ExperimentalTestApi::class)
class TouchInputTest {
    @Test
    fun `a finger dragged up the document scrolls it`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val (_, scroll) = show()

            onRoot().performTouchInput { swipeUp() }
            waitForIdle()

            assertTrue(scroll.firstVisibleItemIndex > 0, "The document did not scroll")
        }

    @Test
    fun `scrolling by touch neither selects nor moves the caret`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // Where the finger went down is where a scroll began, not where the reader wants to
            // write. A caret dropped there would open the block and, on a phone, the keyboard.
            val (state, _) = show()

            onRoot().performTouchInput { swipeUp() }
            waitForIdle()

            assertNull(state.selection, "Scrolling left a selection behind")
            assertNull(state.caret, "Scrolling placed the caret")
        }

    @Test
    fun `a tap places the caret in the block under it`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val (state, _) = show()

            onNodeWithText(paragraph(1)).performTouchInput {
                down(Offset(0f, 2f))
                up()
            }
            waitForIdle()

            val caret = assertNotNull(state.caret, "The tap placed no caret")
            assertEquals(state.blocks[1].id, caret.block)
        }

    @Test
    fun `a mouse dragged down the document selects rather than scrolls`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // The same movement as the swipe above, by the other pointer. The desktop behaviour
            // the touch path was split from, held here beside it so the two cannot drift together.
            val (state, scroll) = show()

            onNodeWithText(paragraph(0)).performMouseInput {
                moveTo(Offset(0f, 2f))
                press()
                moveTo(Offset(0f, HEIGHT / 2))
                release()
            }
            waitForIdle()

            val selection = assertNotNull(state.selection, "The drag selected nothing")
            assertTrue(!selection.isCollapsed, "The drag left only a caret")
            assertEquals(0, scroll.firstVisibleItemIndex, "The drag scrolled the document")
        }

    private fun SkikoComposeUiTest.show(): Pair<EditorState, LazyListState> {
        val text = (0 until PARAGRAPHS).joinToString("\n\n", transform = ::paragraph) + "\n"
        val state = EditorState(DocumentSession(text))
        val scroll = LazyListState()
        setContent { BlockEditor(state = state, scroll = scroll) }
        return state to scroll
    }

    private fun paragraph(index: Int) = "Paragraph number $index, one of many in a document longer than the window."

    private companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 600f

        /** Several windows' worth, so there is somewhere to scroll to. */
        const val PARAGRAPHS = 60
    }
}
