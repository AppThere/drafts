package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.Screenplay
import com.appthere.drafts.editor.engine.BlockParser
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A screenplay laid out as 5.4 sets it: 61 characters to the line, no wider than a printed page,
 * each element at its own indentation -- and 4.5's held role, which keeps a character name where it
 * is while it is being typed.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenplayLayoutTest {
    @Test
    fun `a wide window carries exactly 61 characters to the line`() =
        runSkikoComposeUiTest(size = WIDE) {
            show("${"x".repeat(61)}\n\n${"y".repeat(62)}\n\nz\n")

            val one = heightOf("z")
            assertEquals(one, heightOf("x".repeat(61)), "61 characters did not fit one line")
            assertTrue(heightOf("y".repeat(62)) > one, "62 characters fitted one line")
        }

    @Test
    fun `the column is never wider than a printed page`() =
        runSkikoComposeUiTest(size = WIDE) {
            show(DOCUMENT)

            val width = widthOf(ACTION)
            assertTrue(width.value <= LETTER_WIDTH + TOLERANCE, "The column is ${width.value}dp wide")
        }

    @Test
    fun `a character name is indented from the action by 5_4's inset`() =
        runSkikoComposeUiTest(size = WIDE) {
            show(DOCUMENT)

            val column = widthOf(ACTION)
            val inset = leftOf(CHARACTER) - leftOf(ACTION)
            assertNear(column * Screenplay.Character.insetStart, inset, "character inset")
        }

    @Test
    fun `dialogue is inset from both sides`() =
        runSkikoComposeUiTest(size = WIDE) {
            show(DOCUMENT)

            val column = widthOf(ACTION)
            assertNear(column * Screenplay.Dialogue.insetStart, leftOf(DIALOGUE) - leftOf(ACTION), "start")
            assertNear(
                column * (1 - Screenplay.Dialogue.insetStart - Screenplay.Dialogue.insetEnd),
                widthOf(DIALOGUE),
                "width",
            )
        }

    @Test
    fun `a speech is set without gaps between its lines`() =
        runSkikoComposeUiTest(size = WIDE) {
            // On the page, a name, its parenthetical and its dialogue are consecutive lines. The
            // space after body text is 5.2's, and once leaked in between them.
            show(SPEECH_DOCUMENT)

            val name = onNodeWithText(CHARACTER).getBoundsInRoot()
            val aside = onNodeWithText(ASIDE).getBoundsInRoot()
            val line = onNodeWithText(SPEECH).getBoundsInRoot()
            assertNear(name.bottom, aside.top, "name to parenthetical")
            assertNear(aside.bottom, line.top, "parenthetical to dialogue")
        }

    @Test
    fun `the line being edited is set full width`() =
        runSkikoComposeUiTest(size = WIDE) {
            // A screenplay's roles turn on what is typed, so the line being written is not placed
            // until the caret leaves it: no inset to jump from as the parser changes its mind.
            val state = show(DOCUMENT)
            edit(state, CHARACTER)

            val field = onNode(isFocused()).getBoundsInRoot()
            assertNear(leftOf(ACTION), field.left, "start")
            assertNear(widthOf(ACTION), field.right - field.left, "width")
        }

    @Test
    fun `the role settles once the caret leaves`() =
        runSkikoComposeUiTest(size = WIDE) {
            val state = show(DOCUMENT)
            edit(state, CHARACTER)
            onNode(isFocused()).performTextInput("by")
            waitForIdle()

            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            // No longer a name, the line and the speech under it are one paragraph of action.
            assertNear(leftOf(ACTION), leftOf("${CHARACTER}by\n$SPEECH", substring = true), "settled")
        }

    @Test
    fun `editing a wrapped speech full width does not move the page below it`() =
        runSkikoComposeUiTest(size = WIDE) {
            // Two lines in the dialogue's band, one at full width: the row keeps its two lines while
            // it is edited, so nothing under it moves, now or when the caret leaves.
            val state = show(LONG_SPEECH_DOCUMENT)
            val before = onNodeWithText(ACTION_AFTER).getBoundsInRoot().top

            edit(state, LONG_SPEECH)
            val during = onNodeWithText(ACTION_AFTER).getBoundsInRoot().top
            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()
            val after = onNodeWithText(ACTION_AFTER).getBoundsInRoot().top

            assertEquals(before, during, "The page moved when the speech was revealed")
            assertEquals(before, after, "The page moved when the speech was placed again")
        }

    @Test
    fun `a speech typed a line at a time is placed when the caret leaves`() =
        runSkikoComposeUiTest(size = WIDE) {
            // The name, Enter, the speech: what a screenwriter types. Each line is written full
            // width, and both land at their insets once the caret moves on.
            val state = show("$ACTION\n\n")
            state.place(Caret(state.blocks.last().id, 0))
            waitForIdle()

            onNode(isFocused()).performTextInput(CHARACTER)
            onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            val opened = onNode(isFocused()).getBoundsInRoot().left
            onNode(isFocused()).performTextInput(SPEECH)
            waitForIdle()
            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            assertEquals("$ACTION\n\n$CHARACTER\n$SPEECH", state.text)
            val column = widthOf(ACTION)
            assertNear(leftOf(ACTION), opened, "opened line")
            assertNear(leftOf(ACTION) + column * Screenplay.Dialogue.insetStart, leftOf(SPEECH), "speech")
            assertNear(leftOf(ACTION) + column * Screenplay.Character.insetStart, leftOf(CHARACTER), "name")
        }

    @Test
    fun `typing a character name a letter at a time moves nothing`() =
        runSkikoComposeUiTest(size = WIDE) {
            // Phase 7's acceptance: "Typing a character name doesn't cause indentation to jump." A
            // name is action until it is all capitals with someone speaking under it, so each
            // letter can change what the parser makes of the line.
            val state = show("$ACTION\n\n\n\n$ACTION_AFTER\n")
            state.place(Caret(state.blocks[1].id, 0))
            waitForIdle()
            val start = onNode(isFocused()).getBoundsInRoot().left
            val below = onNodeWithText(ACTION_AFTER).getBoundsInRoot().top

            "BOB".forEach { letter ->
                onNode(isFocused()).performTextInput(letter.toString())
                waitForIdle()
                assertNear(start, onNode(isFocused()).getBoundsInRoot().left, "the line after '$letter'")
                assertNear(below, onNodeWithText(ACTION_AFTER).getBoundsInRoot().top, "the line below after '$letter'")
            }
        }

    /** The caret at the end of the block whose source is [source]. */
    private fun SkikoComposeUiTest.edit(
        state: EditorState,
        source: String,
    ) {
        val block = state.blocks.first { state.sourceOf(it.block) == source }
        state.place(Caret(block.id, source.length))
        waitForIdle()
    }

    private fun SkikoComposeUiTest.show(text: String): EditorState {
        val state = EditorState(DocumentSession(text, BlockParser.Fountain()))
        setContent { DraftsTheme { BlockEditor(state = state) } }
        waitForIdle()
        return state
    }

    private fun SkikoComposeUiTest.leftOf(
        text: String,
        substring: Boolean = false,
    ): Dp = onNodeWithText(text, substring = substring).getBoundsInRoot().left

    private fun SkikoComposeUiTest.widthOf(text: String): Dp =
        onNodeWithText(text).getBoundsInRoot().let { it.right - it.left }

    private fun SkikoComposeUiTest.heightOf(text: String): Dp =
        onNodeWithText(text).getBoundsInRoot().let { it.bottom - it.top }

    private fun assertNear(
        expected: Dp,
        actual: Dp,
        what: String,
    ) = assertTrue(abs(expected.value - actual.value) <= TOLERANCE, "$what: expected $expected, was $actual")

    private companion object {
        val WIDE = Size(1400f, 900f)

        /** 5.4's 6.0-inch text area, at 160dp to the inch. */
        const val LETTER_WIDTH = 960f
        const val TOLERANCE = 1f

        const val ACTION = "Bob walks in."
        const val CHARACTER = "BOB"
        const val SPEECH = "Hello there."
        const val DIALOGUE = SPEECH
        const val ASIDE = "(quietly)"
        const val LONG_SPEECH = "I have told you twice already, and I will not do it again."
        const val ACTION_AFTER = "She leaves."
        const val LONG_SPEECH_DOCUMENT = "$ACTION\n\n$CHARACTER\n$LONG_SPEECH\n\n$ACTION_AFTER\n"
        const val SPEECH_DOCUMENT = "$ACTION\n\n$CHARACTER\n$ASIDE\n$SPEECH\n"
        const val DOCUMENT = "INT. HOUSE - DAY\n\n$ACTION\n\n$CHARACTER\n$SPEECH\n\nCUT TO:\n"
    }
}
