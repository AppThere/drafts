package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
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
    fun `a character name keeps its indentation while lowercase is typed into it`() =
        runSkikoComposeUiTest(size = WIDE) {
            // Typed lowercase, the line stops being a character name and the parser makes it
            // action. 4.5 holds the role until the caret leaves, so the line stays where it was.
            val state = show(DOCUMENT)
            val before = leftOf(CHARACTER)
            val name = state.blocks.first { state.sourceOf(it.block) == CHARACTER }
            state.place(Caret(name.id, CHARACTER.length))
            waitForIdle()

            onNode(isFocused()).performTextInput("by")
            waitForIdle()

            assertNear(before, leftOf("${CHARACTER}by", substring = true), "held character")
        }

    @Test
    fun `the role settles once the caret leaves`() =
        runSkikoComposeUiTest(size = WIDE) {
            val state = show(DOCUMENT)
            val name = state.blocks.first { state.sourceOf(it.block) == CHARACTER }
            state.place(Caret(name.id, CHARACTER.length))
            waitForIdle()
            onNode(isFocused()).performTextInput("by")
            waitForIdle()

            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            // No longer a name, the line and the speech under it are one paragraph of action.
            assertNear(leftOf(ACTION), leftOf("${CHARACTER}by\n$SPEECH", substring = true), "settled")
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
        const val SPEECH_DOCUMENT = "$ACTION\n\n$CHARACTER\n$ASIDE\n$SPEECH\n"
        const val DOCUMENT = "INT. HOUSE - DAY\n\n$ACTION\n\n$CHARACTER\n$SPEECH\n\nCUT TO:\n"
    }
}
