package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The Phase 2 gate criterion for selection: "Selection drag across 50+ blocks is smooth and the
 * highlight is correct."
 *
 * Correctness is asserted; cost is printed, following `GateReportTest` -- the plan asks for the
 * numbers rather than a verdict, and a wall-clock assertion on a shared machine would be a flake
 * generator rather than a gate.
 *
 * The viewport is deliberately tall. A `LazyColumn` only composes what is visible, so a drag across
 * fifty blocks needs fifty blocks on screen; dragging to the edge and waiting for the list to scroll
 * is a feature this prototype does not have, and pretending otherwise by scrolling first would
 * measure something easier than the criterion asks for.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionGateTest {
    @Test
    fun `a drag across fifty blocks selects every one of them correctly`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val document = manyParagraphs()
            val state = show(document)

            onNodeWithText(paragraph(0)).performMouseInput {
                moveTo(Offset(0f, 1f))
                press()
                repeat(DRAG_STEPS) { step -> moveTo(Offset(0f, DRAG_HEIGHT / DRAG_STEPS * (step + 1))) }
                release()
            }

            val span = state.selectedSpan()
            assertTrue(span != null, "The drag selected nothing")
            assertEquals(
                document.substring(span.start.value, span.endExclusive.value),
                state.selectedText(),
                "The copied text is not the source the selection covers",
            )
            assertTrue(
                blocksCovered(state) >= MIN_BLOCKS,
                "Only ${blocksCovered(state)} blocks were covered; the criterion is $MIN_BLOCKS+",
            )
        }

    @Test
    fun `report the cost of dragging a selection`() =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val document = manyParagraphs()
            val state = show(document)
            val timings = mutableListOf<Long>()

            onNodeWithText(paragraph(0)).performMouseInput {
                moveTo(Offset(0f, 1f))
                press()
            }

            // One move per measurement, each followed by settling the composition. Timing the moves
            // inside a single injection block measured nothing useful -- the events are queued, and
            // the hit-test, the recomposition and the highlight all happen afterwards. What is
            // wanted is the cost of one step of the drag, end to end.
            val stride = DRAG_HEIGHT / (DRAG_STEPS + WARMUP)
            repeat(DRAG_STEPS + WARMUP) { step ->
                val y = stride * (step + 1)
                val started = TimeSource.Monotonic.markNow()
                onNodeWithText(paragraph(0)).performMouseInput { moveTo(Offset(0f, y)) }
                waitForIdle()
                timings += started.elapsedNow().inWholeMicroseconds
            }

            onNodeWithText(paragraph(0)).performMouseInput { release() }

            val measured = timings.drop(WARMUP).sorted()
            val idle = idleFrames()
            println(
                """
                |
                |Phase 2 gate -- selection measurements
                |  document:       ${document.length} code units, ${state.blocks.size} blocks
                |  blocks covered: ${blocksCovered(state)}
                |  drag updates:   ${measured.size}
                |  median update:  ${measured[measured.size / 2]} microseconds
                |  slowest update: ${measured.last()} microseconds
                |  idle frame:     $idle microseconds (same loop, nothing selected)
                |  selection cost: ${measured[measured.size / 2] - idle} microseconds above idle
                |  frame budget:   8333 microseconds at 120Hz
                """.trimMargin(),
            )
        }

    /**
     * The same loop with nothing selected, as a control.
     *
     * Without it the measurement is unreadable: settling a composition of eighty text blocks in the
     * test harness costs milliseconds whether or not anything is selected, and attributing that to
     * the selection layer would condemn it for work it does not do.
     */
    private fun SkikoComposeUiTest.idleFrames(): Long {
        val timings = mutableListOf<Long>()
        val stride = DRAG_HEIGHT / (DRAG_STEPS + WARMUP)

        repeat(DRAG_STEPS + WARMUP) { step ->
            val started = TimeSource.Monotonic.markNow()
            onNodeWithText(paragraph(0)).performMouseInput { moveTo(Offset(0f, stride * (step + 1))) }
            waitForIdle()
            timings += started.elapsedNow().inWholeMicroseconds
        }

        return timings.drop(WARMUP).sorted()[(timings.size - WARMUP) / 2]
    }

    /** How many blocks the selection touches, which is what "across 50+ blocks" means. */
    private fun blocksCovered(state: EditorState): Int {
        val span = state.selectedSpan() ?: return 0
        return state.blocks.count { editorBlock ->
            val own = editorBlock.block.source ?: return@count false
            own.start.value < span.endExclusive.value && own.endExclusive.value > span.start.value
        }
    }

    private fun SkikoComposeUiTest.show(document: String): EditorState {
        val state = EditorState(DocumentSession(document))
        setContent { BlockEditor(state = state) }
        return state
    }

    private fun manyParagraphs() = (0 until PARAGRAPHS).joinToString("\n\n") { paragraph(it) } + "\n"

    private fun paragraph(index: Int) = "Paragraph number $index."

    private companion object {
        const val WIDTH = 1200f

        /** Tall enough to compose well over fifty blocks at once. */
        const val HEIGHT = 2600f

        const val PARAGRAPHS = 140
        const val DRAG_STEPS = 60
        const val WARMUP = 10
        const val MIN_BLOCKS = 50

        /** How far down the viewport the drag travels. */
        const val DRAG_HEIGHT = 2500f
    }
}
