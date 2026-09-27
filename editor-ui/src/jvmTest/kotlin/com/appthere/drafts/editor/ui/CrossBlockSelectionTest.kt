package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The cross-block selection layer, which `appthere-drafts.md` 4.4 calls the largest unknown in the
 * project and asks to be prototyped *before* committing to per-block fields.
 *
 * `SelectionTest` in `:editor-engine` proves the model: given two positions, the right source comes
 * back. What is unproven until here is everything between a pointer and those two positions --
 * finding which block is under a point, turning a point into an offset in preview text that does
 * not match the file, and getting the drag at all when a scrolling list wants the same gesture.
 */
@OptIn(ExperimentalTestApi::class)
class CrossBlockSelectionTest {
    @Test
    fun `a click past the end of the text lands the caret at the end of the source`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // The sharp end of the preview map. "Alpha *beta* gamma." is nineteen characters; what
            // is drawn is seventeen. A click past the last glyph is preview offset seventeen, and
            // the caret belongs at nineteen -- the end of the *source*. A layer that skipped the
            // map would answer seventeen, which is in the middle of the closing marker.
            val state = show(MARKED_UP, LAST)

            onNodeWithText("Alpha beta gamma.").performMouseInput {
                moveTo(Offset(PAST_THE_TEXT, 2f))
                press()
                release()
            }

            val caret = assertNotNull(state.caret, "The click placed no caret")
            assertEquals(state.blocks[0].id, caret.block)
            assertEquals(MARKED_UP.length, caret.offset)
        }

    @Test
    fun `a click at the start of the text lands at the start of the source`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show(MARKED_UP, LAST)

            onNodeWithText("Alpha beta gamma.").performMouseInput {
                moveTo(Offset(0f, 2f))
                press()
                release()
            }

            assertEquals(0, assertNotNull(state.caret).offset)
        }

    @Test
    fun `a click in a later block lands at an offset into that block`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // `Caret.offset` is an offset into the block, not into the document. Testing this on the
            // first block proves nothing, because there the two are the same number.
            val state = show(FIRST, MARKED_UP, LAST)

            onNodeWithText("Alpha beta gamma.").performMouseInput {
                moveTo(Offset(PAST_THE_TEXT, 2f))
                press()
                release()
            }

            val caret = assertNotNull(state.caret, "The click placed no caret")
            assertEquals(state.blocks[1].id, caret.block)
            assertEquals(MARKED_UP.length, caret.offset, "The caret offset is a document offset")
        }

    @Test
    fun `a drag across blocks selects the source between them`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show(FIRST, SECOND, LAST)

            onNodeWithText(FIRST).performMouseInput {
                moveTo(Offset(0f, 2f))
                press()
                moveTo(intoTheSecondBlock())
                release()
            }

            val selected = state.selectedText()
            assertTrue(selected.startsWith(FIRST), "Selection did not start at the first block: $selected")
            assertTrue(SECOND.take(4) in selected, "Selection did not reach the second block: $selected")
            assertTrue("\n\n" in selected, "The blank line between blocks is part of the raw source")
        }

    @Test
    fun `a drag leaves no caret because no single field holds the selection`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show(FIRST, SECOND, LAST)

            onNodeWithText(FIRST).performMouseInput {
                moveTo(Offset(0f, 2f))
                press()
                moveTo(intoTheSecondBlock())
                release()
            }

            assertEquals(null, state.caret, "A cross-block selection should not be in a field")
            assertTrue(state.selection?.isCollapsed == false)
        }

    @Test
    fun `select all then copy yields the whole document as source`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // A gate criterion. The copied text is the file, markup and all, not what is on screen.
            val state = show(FIRST, SECOND, LAST)

            state.selectAll()
            waitForIdle()

            assertEquals("$FIRST\n\n$SECOND\n\n$LAST", state.selectedText())
        }

    @Test
    fun `the highlight covers the selected part of each block and no more`() {
        // "the highlight is correct", in the only form a test can check it: every block that the
        // selection touches reports a preview range, blocks outside it report none, and a partly
        // selected block reports part of itself.
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show(FIRST, SECOND, LAST)
            state.selectAll()
            waitForIdle()

            val ranges = state.blocks.map { rangeIn(state, it.block) }

            assertTrue(ranges.all { it != null }, "A selected block drew no highlight: $ranges")
            assertEquals(FIRST.length, ranges.first()!!.last, "The first block is highlighted in full")
        }
    }

    @Test
    fun `a selection in one block does not highlight its neighbours`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = show(FIRST, SECOND, LAST)
            val first = state.blocks[0]

            state.beginSelection(Caret(first.id, 0))
            state.extendSelection(Caret(first.id, 5))
            waitForIdle()

            assertEquals(0..5, rangeIn(state, first.block))
            assertEquals(null, rangeIn(state, state.blocks[1].block))
        }

    @Test
    fun `ctrl-A selects the document without clicking first`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // No click first. The host takes focus when the document opens, so the very first
            // keystroke has somewhere to travel -- which it did not before, and 10.2's "complete
            // keyboard operation" has to include the first one.
            val state = show(FIRST, SECOND, LAST)

            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.A)
                keyUp(Key.CtrlLeft)
            }

            assertEquals("$FIRST\n\n$SECOND\n\n$LAST", state.selectedText())
        }

    /** The preview range of [block] that the current selection covers, as the highlight uses it. */
    private fun rangeIn(
        state: EditorState,
        block: Block,
    ): IntRange? {
        val selected = state.selectedSpan()
        val own = block.source
        if (selected == null || own == null) return null

        val from = maxOf(selected.start.value, own.start.value)
        val to = minOf(selected.endExclusive.value, own.endExclusive.value)

        val start = own.start.value
        return if (from < to) {
            previewOfBlock(block, Color.Unspecified).previewRangeOf(from - start, to - start)
        } else {
            null
        }
    }

    /**
     * Where to drag to: the middle of the second block, across as well as down.
     *
     * Both parts were wrong before. The distance was a constant, which stopped reaching the second
     * block once the design system gave blocks their real spacing. And the drag was straight down
     * the left edge, which lands on offset zero of whatever block it reaches -- a selection that
     * ends exactly where the second block starts and so contains none of it. Measuring the
     * position, and moving across the line as a hand would, fixes both.
     */
    private fun SkikoComposeUiTest.intoTheSecondBlock(): Offset {
        val from = onNodeWithText(FIRST).getBoundsInRoot().top
        val second = onNodeWithText(SECOND).getBoundsInRoot()

        return Offset(INTO_THE_LINE, ((second.top + second.bottom) / 2 - from).value)
    }

    private fun SkikoComposeUiTest.show(vararg blocks: String): EditorState {
        val state = EditorState(DocumentSession(blocks.joinToString("\n\n") + "\n"))
        setContent { HostedEditor(state) }
        return state
    }

    private companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 900f

        const val MARKED_UP = "Alpha *beta* gamma."

        /** Comfortably right of the last glyph, so the click lands past the end of the line. */
        const val PAST_THE_TEXT = 600f

        const val FIRST = "First paragraph of the document."
        const val SECOND = "Second paragraph, a little further down."
        const val LAST = "A final paragraph."

        /** Far enough along the line to be inside the words rather than at their edge. */
        const val INTO_THE_LINE = 120f
    }
}
