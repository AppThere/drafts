package com.appthere.drafts.editor.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.HeightClass
import com.appthere.drafts.design.LocalWindowSize
import com.appthere.drafts.design.Screenplay
import com.appthere.drafts.design.WidthClass
import com.appthere.drafts.design.WindowSize
import com.appthere.drafts.editor.engine.BlockParser
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 5.4's dual dialogue: "a two-column layout at the 16.7%/25% insets, splitting the available width.
 * On a compact window it stacks vertically with a connecting rule and a 'simultaneous' marker." And
 * the pair being written is stacked whatever the window, until the caret leaves it.
 */
@OptIn(ExperimentalTestApi::class)
class DualDialogueTest {
    @Test
    fun `two speeches at once are set side by side`() =
        runSkikoComposeUiTest(size = WIDE) {
            show(DOCUMENT)

            val brick = onNodeWithText(BRICK_LINE).getBoundsInRoot()
            val steel = onNodeWithText(STEEL_LINE).getBoundsInRoot()
            assertNear(
                onNodeWithText(BRICK).getBoundsInRoot().top,
                onNodeWithText(STEEL).getBoundsInRoot().top,
                "names",
            )
            assertTrue(steel.left >= brick.right, "The second speech overlaps the first: $brick, $steel")
        }

    @Test
    fun `the two columns split dialogue's band`() =
        runSkikoComposeUiTest(size = WIDE) {
            show(DOCUMENT)

            val column = widthOf(ACTION)
            val start = leftOf(ACTION) + column * Screenplay.Dialogue.insetStart
            val end = leftOf(ACTION) + column * (1 - Screenplay.Dialogue.insetEnd)
            assertNear(start, leftOf(BRICK_LINE), "the first column's start")
            assertNear(end, onNodeWithText(STEEL_LINE).getBoundsInRoot().right, "the second column's end")
            assertNear(widthOf(BRICK_LINE), widthOf(STEEL_LINE), "the columns' widths")
        }

    @Test
    fun `the action after a pair is below the longer speech`() =
        runSkikoComposeUiTest(size = WIDE) {
            show(DOCUMENT)

            val after = onNodeWithText(AFTER).getBoundsInRoot().top
            assertTrue(after > onNodeWithText(STEEL_LINE).getBoundsInRoot().bottom, "The action overlaps the speech")
        }

    @Test
    fun `a compact window stacks the pair and marks the second speech`() =
        runSkikoComposeUiTest(size = NARROW) {
            show(DOCUMENT, compact = true)

            assertTrue(
                onNodeWithText(STEEL).getBoundsInRoot().top >= onNodeWithText(BRICK_LINE).getBoundsInRoot().bottom,
                "The pair was set side by side on a compact window",
            )
            onAllNodesWithText(MARKER, useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `side by side the pair carries no marker`() =
        runSkikoComposeUiTest(size = WIDE) {
            show(DOCUMENT)

            onAllNodesWithText(MARKER, useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `the second name is said to be simultaneous`() =
        runSkikoComposeUiTest(size = WIDE) {
            show(DOCUMENT)

            onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("$MARKER, $STEEL")))
                .assertExists()
        }

    @Test
    fun `the pair being written is stacked until the caret leaves it`() =
        runSkikoComposeUiTest(size = WIDE) {
            val state = show(DOCUMENT)

            state.place(Caret(state.blocks.first { state.sourceOf(it.block) == BRICK_LINE }.id, 0))
            waitForIdle()
            assertTrue(
                onNodeWithText(STEEL).getBoundsInRoot().top > onNode(isFocused()).getBoundsInRoot().bottom,
                "The pair stayed side by side with the caret in it",
            )

            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()
            assertNear(
                onNodeWithText(BRICK).getBoundsInRoot().top,
                onNodeWithText(STEEL).getBoundsInRoot().top,
                "the names once the caret left",
            )
        }

    @Test
    fun `marking a name keeps the field it is typed in`() =
        runSkikoComposeUiTest(size = WIDE) {
            // Typing the `^` makes a pair around the caret, and the keys after it still go to the
            // name being typed.
            val state = show("$ACTION\n\n$BRICK\n$BRICK_LINE\n\n$STEEL\n$STEEL_LINE\n\n$AFTER\n")
            val name = state.blocks.first { state.sourceOf(it.block) == STEEL }
            state.place(Caret(name.id, STEEL.length))
            waitForIdle()

            onNode(isFocused()).performTextInput(" ^")
            waitForIdle()

            assertEquals(1, state.dualPairs.size, "No pair was made")
            assertEquals(name.id, assertNotNull(state.caret).block, "The caret left the name")
            onNode(isFocused()).performTextInput("!")
            waitForIdle()
            assertTrue("$STEEL ^!\n" in state.text, "The key after the marker went elsewhere: ${state.text}")
        }

    @Test
    fun `a click in the second column lands in the second speech`() =
        runSkikoComposeUiTest(size = WIDE) {
            val state = show(DOCUMENT)

            onNodeWithText(STEEL_LINE).performMouseInput {
                moveTo(Offset(2f, 2f))
                press()
                release()
            }

            val line = state.blocks.first { state.sourceOf(it.block) == STEEL_LINE }
            assertEquals(line.id, assertNotNull(state.caret, "The click placed no caret").block)
        }

    @Test
    fun `a caret below many pairs is brought on screen and keeps the keys`() =
        runSkikoComposeUiTest(size = Size(1400f, 400f)) {
            // Each pair above is one row of the list for several blocks. Scrolling to the block's
            // index instead of its row's lands well past it, where the block has no field.
            val pairs = "$BRICK\n$BRICK_LINE\n\n$STEEL ^\n$STEEL_LINE\n\n$ACTION\n\n".repeat(PAIRS)
            val state = show("$pairs$AFTER\n")
            // The action after the sixth pair: block 35, row 11. Not the last block, which any
            // index past the end of the list would reach.
            val target = state.blocks.filter { state.sourceOf(it.block) == ACTION }[TARGET].id

            state.place(Caret(target, 0))
            waitForIdle()
            onNode(isFocused()).performTextInput("Then ")
            waitForIdle()

            assertEquals("Then $ACTION", state.blockOf(target)?.let(state::sourceOf))
        }

    private fun SkikoComposeUiTest.show(
        text: String,
        compact: Boolean = false,
    ): EditorState {
        val state = EditorState(DocumentSession(text, BlockParser.Fountain()))
        val window =
            WindowSize(if (compact) WidthClass.Compact else WidthClass.Expanded, HeightClass.Expanded)
        setContent {
            DraftsTheme {
                CompositionLocalProvider(LocalWindowSize provides window) { BlockEditor(state = state) }
            }
        }
        waitForIdle()
        return state
    }

    private fun SkikoComposeUiTest.leftOf(text: String): Dp = onNodeWithText(text).getBoundsInRoot().left

    private fun SkikoComposeUiTest.widthOf(text: String): Dp =
        onNodeWithText(text).getBoundsInRoot().let { it.right - it.left }

    private fun assertNear(
        expected: Dp,
        actual: Dp,
        what: String,
    ) = assertTrue(abs(expected.value - actual.value) <= TOLERANCE, "$what: expected $expected, was $actual")

    private companion object {
        val WIDE = Size(1400f, 900f)
        val NARROW = Size(400f, 900f)
        const val TOLERANCE = 1f
        const val PAIRS = 20
        const val TARGET = 5

        const val MARKER = "Simultaneous"
        const val ACTION = "They face each other across the table, neither willing to look away first."
        const val BRICK = "BRICK"
        const val BRICK_LINE = "Screw retirement."
        const val STEEL = "STEEL"
        const val STEEL_LINE = "Screw retirement, and screw you."
        const val AFTER = "They laugh."
        const val DOCUMENT = "$ACTION\n\n$BRICK\n$BRICK_LINE\n\n$STEEL ^\n$STEEL_LINE\n\n$AFTER\n"
    }
}
