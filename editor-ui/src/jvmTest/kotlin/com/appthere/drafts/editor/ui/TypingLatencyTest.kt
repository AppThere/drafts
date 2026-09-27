package com.appthere.drafts.editor.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.engine.sourceOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The first Phase 2 gate criterion: "Typing latency stays below one frame at 120Hz."
 *
 * `GateReportTest` in `:editor-engine` measures the engine alone -- parse and reconcile, no layout,
 * no draw -- and calls that a floor on the real cost rather than an estimate of it. This is the rest
 * of it: a keystroke through the composition, on a document of ten thousand words, with the block
 * list, the reserved heights and the preview maps all in play.
 *
 * Reported rather than asserted, for the reason `GateReportTest` gives: a wall-clock assertion on a
 * shared machine is a flake generator rather than a gate. The margin here is thin enough that an
 * assertion would fail on a busy build agent while the editor was no slower than it is now. What is
 * asserted is correctness -- that typing a long document leaves the rest of it alone.
 *
 * Three caveats stated plainly, because a measurement whose limits are not written down gets quoted
 * as if it had none:
 *
 * - The number here includes the test harness settling a composition, which a real frame does not.
 *   The control below measures a frame in which nothing changed, so the cost attributable to the
 *   keystroke is the difference, and both are reported.
 * - The criterion says "on the slowest target device". This is a desktop JVM on a developer
 *   machine. Phase 6 is when a phone exists to run it on; until then the mobile half of the
 *   criterion is unmeasured, and no number here should be read as having answered it.
 * - The median is within budget and the slowest keystroke is not. Typing that is smooth on average
 *   and stutters occasionally is what that combination feels like, so the tail is printed too and
 *   should not be dropped from the report.
 */
@OptIn(ExperimentalTestApi::class)
class TypingLatencyTest {
    @Test
    fun `the fixture really is ten thousand words`() {
        // The criterion names the size, so the size is asserted rather than assumed. `:editor-engine`
        // has a fixture of its own; the two are not shared, and do not need to be, because each is
        // checked against the property the plan actually states.
        val words = gateDocument().split(Regex("\\s+")).count { it.isNotBlank() }

        assertTrue(words >= TEN_THOUSAND, "The gate fixture is only $words words")
    }

    @Test
    fun `typing into a ten thousand word document stays within a frame`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val document = gateDocument()
            val state = EditorState(DocumentSession(document))
            setContent { BlockEditor(state = state) }

            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            val typed = measureTyping(state)
            val idle = measureIdle(state)
            val stateOnly = measureStateEdits(state)
            val floor = harnessFloor()
            val attributable = typed[typed.size / 2] - floor

            println(
                """
                |
                |Phase 2 gate -- typing latency through the composition
                |  document:        ${document.length} code units, ${state.blocks.size} blocks
                |  keystrokes:      ${typed.size}
                |  median:          ${typed[typed.size / 2]} microseconds
                |  slowest:         ${typed.last()} microseconds
                |  lookup only:     $idle microseconds (finding the node, no typing)
                |  engine + recompose: $stateOnly microseconds (no field involved)
                |  harness floor:   $floor microseconds (one trivial recomposition)
                |  attributable:    $attributable microseconds above the floor
                |  frame budget:    $FRAME_BUDGET microseconds at 120Hz
                """.trimMargin(),
            )

            println(
                if (attributable < FRAME_BUDGET) {
                    "  verdict:         within budget on the median, by ${FRAME_BUDGET - attributable}us"
                } else {
                    "  verdict:         OVER by ${attributable - FRAME_BUDGET}us on the median"
                },
            )
        }

    @Test
    fun `typing into a long document does not disturb the rest of it`() {
        // Latency is worth nothing if the document is wrong afterwards. The control on the
        // measurement above: the text is what was typed, and the blocks below kept their identity.
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = EditorState(DocumentSession(gateDocument()))
            setContent { BlockEditor(state = state) }

            val first = state.blocks.first()
            val tailBefore = state.blocks.takeLast(TAIL_SAMPLE).map { it.id }
            val before = state.sourceOf(first.block)

            state.place(Caret(first.id, 0))
            waitForIdle()
            onNodeWithText(before).performTextInput("x")

            assertEquals("x$before", state.sourceOf(state.blocks.first().block))
            assertEquals(tailBefore, state.blocks.takeLast(TAIL_SAMPLE).map { it.id })
        }
    }

    /** One keystroke per measurement, each settled, sorted so the median and the tail are readable. */
    private fun SkikoComposeUiTest.measureTyping(state: EditorState): List<Long> {
        val timings = mutableListOf<Long>()

        repeat(KEYSTROKES + WARMUP) {
            val showing = state.sourceOf(state.blocks.first().block)
            val started = TimeSource.Monotonic.markNow()
            onNodeWithText(showing).performTextInput("x")
            waitForIdle()
            timings += started.elapsedNow().inWholeMicroseconds
        }

        return timings.drop(WARMUP).sorted()
    }

    /**
     * What a single trivial state change costs in this harness, with nothing of ours in it.
     *
     * Settling a composition here is not free the way a real frame is: one `BasicText` whose string
     * changes measures at well over a millisecond. That is the harness, and subtracting it is what
     * makes the rest of the number about this editor.
     *
     * The subtraction assumes the overhead is fixed rather than proportional to the size of the
     * composition, which is an assumption and not a measurement. It is the reason the raw median is
     * reported next to it rather than replaced by it.
     */
    private fun harnessFloor(): Long {
        val timings = mutableListOf<Long>()

        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            var text by mutableStateOf("x")
            setContent { BasicText(text) }
            waitForIdle()

            repeat(KEYSTROKES + WARMUP) { index ->
                val started = TimeSource.Monotonic.markNow()
                text = "x".repeat(index % VARIETY + 1)
                waitForIdle()
                timings += started.elapsedNow().inWholeMicroseconds
            }
        }

        return timings.drop(WARMUP).sorted()[KEYSTROKES / 2]
    }

    /**
     * The same edits made directly on the state, bypassing the field.
     *
     * Splits the cost in two: everything here is the engine plus the recomposition, so whatever the
     * input path costs beyond this belongs to `BasicTextField` receiving the keystroke.
     */
    private fun SkikoComposeUiTest.measureStateEdits(state: EditorState): Long {
        val timings = mutableListOf<Long>()

        repeat(KEYSTROKES + WARMUP) {
            val block = state.blocks.first()
            val span = block.block.source!!
            val showing = state.sourceOf(block.block)

            val started = TimeSource.Monotonic.markNow()
            state.replace(span, "x$showing", 1)
            waitForIdle()
            timings += started.elapsedNow().inWholeMicroseconds
        }

        return timings.drop(WARMUP).sorted()[KEYSTROKES / 2]
    }

    /**
     * The same loop without the typing, so the keystroke's own cost can be separated out.
     *
     * It has to do the node lookup too. Finding a node by its text walks the semantics tree, which
     * is the test harness working rather than the editor, and it happens inside the timed region.
     * A control that only settled the composition measured nothing at all -- it came back at zero
     * microseconds and made every microsecond of harness overhead look like typing latency.
     */
    private fun SkikoComposeUiTest.measureIdle(state: EditorState): Long {
        val timings = mutableListOf<Long>()

        repeat(KEYSTROKES + WARMUP) {
            val showing = state.sourceOf(state.blocks.first().block)
            val started = TimeSource.Monotonic.markNow()
            onNodeWithText(showing).assertIsDisplayed()
            waitForIdle()
            timings += started.elapsedNow().inWholeMicroseconds
        }

        return timings.drop(WARMUP).sorted()[KEYSTROKES / 2]
    }

    /**
     * Ten thousand words with the structures that make reparse interesting.
     *
     * Headings, lists, quotes and inline markup, so that a careless dirty window has something to
     * get wrong. Mirrors the shape of `:editor-engine`'s own gate fixture without sharing it: a
     * module existing only to hold a string generator would cost more than the duplication, and the
     * size assertion above is what actually keeps either of them honest.
     */
    private fun gateDocument(): String =
        buildString {
            repeat(SECTIONS) { section ->
                appendLine("## Section $section")
                appendLine()
                repeat(PARAGRAPHS_PER_SECTION) { paragraph ->
                    appendLine(
                        "Paragraph $paragraph of section $section with *emphasis*, `code`, and a " +
                            "[link](https://example.com) in it, written out at enough length to " +
                            "make the document a realistic size for the gate criterion.",
                    )
                    appendLine()
                }
                appendLine("- first item")
                appendLine("- second item")
                appendLine()
                appendLine("> A quoted line.")
                appendLine()
            }
        }

    private companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 900f

        const val SECTIONS = 64
        const val PARAGRAPHS_PER_SECTION = 5
        const val TEN_THOUSAND = 10_000

        const val KEYSTROKES = 40
        const val WARMUP = 10
        const val TAIL_SAMPLE = 5

        /** So the trivial recomposition changes its text rather than repeating one value. */
        const val VARIETY = 7

        /** One frame at 120Hz, in microseconds. */
        const val FRAME_BUDGET = 8333
    }
}
