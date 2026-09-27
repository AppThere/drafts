package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.Prose
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reveal/preview geometry at a real window size.
 *
 * The default test viewport is narrower than the editor's measure, so both states simply fill it
 * and any difference between them is invisible. A gate criterion measured in conditions that cannot
 * fail it is not a measurement, so these run at something like a real window.
 */
@OptIn(ExperimentalTestApi::class)
class WideViewportTest {
    @Test
    fun `the column stops at its measure however wide the window`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // 5.3 clamps the content column to 34em -- about 612dp at the 18sp default -- and lets
            // the rest become margin. This did not work for two phases: `fillMaxWidth` before
            // `widthIn` fixes the width at the window and leaves the cap nothing to do, so the
            // measure was quietly ignored and lines ran the full width of the window.
            showDocument()

            val width = onNodeWithText(LAST).getBoundsInRoot().let { it.right - it.left }

            assertTrue(
                width.value <= MEASURE_AT_DEFAULT_BASE + TOLERANCE,
                "The column is ${width.value}dp wide, past the ${MEASURE_AT_DEFAULT_BASE}dp measure",
            )
        }

    @Test
    fun `a revealed block is the width of the preview it replaced`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            showDocument()
            val preview = onNodeWithText(PREVIEW).getBoundsInRoot().let { it.right - it.left }

            onNodeWithText(PREVIEW).performClick()

            val reveal = onNodeWithText(REVEAL).getBoundsInRoot().let { it.right - it.left }
            assertEquals(preview, reveal, "The field is not the width of the preview it replaced")
        }

    @Test
    fun `revealing a wrapped paragraph does not move the document below it`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // 4.2 names this exact risk: "Since markup characters are *added* in reveal state, a
            // paragraph near a wrap boundary can gain a line."
            showDocument()
            val before = onNodeWithText(LAST).getBoundsInRoot().top

            onNodeWithText(PREVIEW).performClick()

            val after = onNodeWithText(LAST).getBoundsInRoot().top
            assertEquals(before, after, "The document moved by ${after - before} on reveal")
        }

    @Test
    fun `a hard-wrapped paragraph reserves no slack at all`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // The case that cost 28% of a real screenplay's height. A paragraph the author wrapped
            // is one line in preview and used to be two in reveal, so every such block reserved a
            // line it only needed in one state. Reveal now marks the break instead of obeying it,
            // so the two states are the same shape and there is nothing to reserve.
            val state = EditorState(DocumentSession(HARD_WRAPPED))
            setContent { BlockEditor(state = state) }
            val before = onNodeWithText(LAST).getBoundsInRoot().top
            val preview = onNodeWithText(HARD_WRAPPED_PREVIEW).getBoundsInRoot().let { it.bottom - it.top }

            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            val after = onNodeWithText(LAST).getBoundsInRoot().top
            val reveal = onNodeWithText(HARD_WRAPPED_REVEALED).getBoundsInRoot().let { it.bottom - it.top }

            assertEquals(before, after, "The document moved by ${after - before} on reveal")
            assertTrue(
                abs((reveal - preview).value) < halfALineOfBody(),
                "The block reserves ${abs((reveal - preview).value)}dp of slack, about a line",
            )
        }

    @Test
    fun `no kind of block moves the document when it reveals`() {
        // One case proves the mitigation works; this says it works for the shapes that differ most
        // between the two states. Each entry is a block whose reveal is taller than its preview for
        // a different reason, and the assertion is the same every time: the paragraph below it does
        // not move.
        val cases =
            mapOf(
                "hard-wrapped paragraph" to HARD_WRAPPED_SOURCE,
                "paragraph with hidden markup" to "A *hard* case with **several** hidden `markers` in it.",
                "fenced code block, fences hidden in preview" to "```kotlin\nfun main() {\n}\n```",
                "heading, marker hidden in preview" to "### A heading with its hashes hidden",
                "list, markers replaced by bullets" to "- first item\n- second item\n- third item",
                "quote, marker replaced by a rule" to "> A quotation with its angle bracket hidden.",
            )

        val moved = cases.mapNotNull { (name, source) -> shiftOf(name, source) }

        assertEquals(emptyList(), moved, "Revealing these blocks moved the document below them")
    }

    @Test
    fun `a paragraph near a wrap boundary does not move the document when it reveals`() =
        runSkikoComposeUiTest(size = Size(NARROW, HEIGHT)) {
            // 4.2's own example, and the reason the requirement exists: "Since markup characters
            // are *added* in reveal state, a paragraph near a wrap boundary can gain a line."
            // The width is not arbitrary. Whether a given paragraph sits near a boundary depends
            // on the measure, so widths were scanned to find one where this text gains a line in
            // reveal -- at 420 it does not, and the test would have passed either way.
            val state = EditorState(EditorDocument.session(MARKER_HEAVY, LAST))
            setContent { BlockEditor(state = state) }
            val before = onNodeWithText(LAST).getBoundsInRoot().top

            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            val after = onNodeWithText(LAST).getBoundsInRoot().top
            assertEquals(before, after, "The document moved by ${after - before} on reveal")
        }

    @Test
    fun `a fenced code block shows its fences and so reserves no slack`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // Hiding the fences cost two lines of reserved slack under every code block in the
            // document, in every state, to pay for a transition. Showing them dimmed makes the two
            // states the same shape, so there is nothing to reserve.
            //
            // The two states are now identical for a fenced block, which is right: a code block has
            // no inline decoration to reveal. The height assertion is what says the slack is gone.
            val state = EditorState(EditorDocument.session(FENCED, LAST))
            setContent { BlockEditor(state = state) }

            val preview = onNodeWithText(FENCED).getBoundsInRoot().let { it.bottom - it.top }

            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            val reveal = onNodeWithText(FENCED).getBoundsInRoot().let { it.bottom - it.top }
            val difference = abs((reveal - preview).value)

            // Near-zero rather than exactly zero. `BasicText` and `BasicTextField` measure the same
            // string a couple of dp apart once a real font's metrics are involved, which is font
            // rounding and not hidden markup. What the test is for is the two *lines* that hiding
            // the fences used to cost, so the bar is half a line of code.
            assertTrue(
                difference < halfALineOfCode(),
                "The fenced block reserves ${difference}dp of slack, about a line of code",
            )
        }

    @Test
    fun `an indented code block keeps its indent in preview`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            // 4.1 lists "block-level indentation and alignment" among the things reveal preserves.
            // Preview used to drop the four spaces, so the code sat left of where the caret would
            // put it and jumped across when focus arrived.
            //
            // Finding a node whose text carries the indent is what proves it; the bounds check
            // below only guards against the block being laid out differently in the two states.
            val state = EditorState(EditorDocument.session(INDENTED, LAST))
            setContent { BlockEditor(state = state) }

            val preview = onNodeWithText(INDENTED).getBoundsInRoot().left

            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            val reveal = onNodeWithText(INDENTED).getBoundsInRoot().left
            assertEquals(preview, reveal, "The block moved sideways on reveal")
        }

    /** The distance the block below moves when [source] reveals, or null if it does not move. */
    private fun shiftOf(
        name: String,
        source: String,
    ): String? {
        var shift: String? = null
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = EditorState(DocumentSession("$source\n\n$LAST\n"))
            setContent { BlockEditor(state = state) }
            val before = onNodeWithText(LAST).getBoundsInRoot().top

            state.place(Caret(state.blocks[0].id, 0))
            waitForIdle()

            val after = onNodeWithText(LAST).getBoundsInRoot().top
            if (after != before) shift = "$name moved by ${after - before}"
        }
        return shift
    }

    /** Half a line of body text: the threshold between metric rounding and a whole hidden line. */
    private fun halfALineOfBody(): Float = Prose.Body.sizeAt(Prose.DefaultBase).value * Prose.Body.lineHeight / 2

    /** Half a line of code at the default base: the threshold between rounding and a hidden line. */
    private fun halfALineOfCode(): Float = Prose.Code.sizeAt(Prose.DefaultBase).value * Prose.Code.lineHeight / 2

    private fun SkikoComposeUiTest.showDocument() {
        val state = EditorState(DocumentSession(DOCUMENT))
        setContent { BlockEditor(state = state) }
    }

    private companion object {
        const val WIDTH = 1600f

        /** Chosen by measurement: at this width this paragraph gains a line in reveal. */
        const val NARROW = 480f
        const val HEIGHT = 1200f

        /** 34em at the 18sp default, as 5.3 works it out. */
        const val MEASURE_AT_DEFAULT_BASE = 612f
        const val TOLERANCE = 1f

        /** Long enough to wrap at this width, so an extra character can cost a whole line. */
        const val REVEAL =
            "A *What You See Is What You Mean* editor. Click into any block to reveal its markup; " +
                "click away and it returns to preview, and the line should not move at all."
        const val PREVIEW =
            "A What You See Is What You Mean editor. Click into any block to reveal its markup; " +
                "click away and it returns to preview, and the line should not move at all."
        const val LAST = "A final paragraph."

        /** Preview and reveal are the same text: the fences are shown, dimmed. */
        const val FENCED = "```kotlin\nfun main() {\n}\n```"

        /** Indented rather than fenced: the indent is the markup, and preview keeps it. */
        const val INDENTED = "    fun main() {\n    }"

        const val DOCUMENT = "$REVEAL\n\n$LAST\n"

        /** Hard-wrapped by the author: one line in preview, and now one in reveal too. */
        const val HARD_WRAPPED_SOURCE = "Short line one\nand a second source line."
        const val HARD_WRAPPED_PREVIEW = "Short line one and a second source line."

        /** What reveal shows: the same shape, with the author's break marked rather than obeyed. */
        const val HARD_WRAPPED_REVEALED = "Short line one↵and a second source line."
        const val HARD_WRAPPED = "$HARD_WRAPPED_SOURCE\n\n$LAST\n"

        /** Enough hidden markers that reveal costs a line at a narrow measure. */
        const val MARKER_HEAVY =
            "A *hard* case with **several** hidden `markers` in it, and *more* after " +
                "**that**, so the `added` characters push the *last* word over."
    }
}
