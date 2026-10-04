package com.appthere.drafts.editor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.editor.engine.DocumentSession
import kotlinx.coroutines.runBlocking
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The scroll third of the recurring performance gate.
 *
 * `IMPLEMENTATION-PLAN.md`: "10,000-word fixture, slowest target device. Typing latency, scroll,
 * reparse bounds." Typing has `TypingLatencyTest` and reparse has `:editor-engine`'s
 * `GateReportTest`; scroll had nothing, so the gate has been passing on two thirds of its subject
 * since Phase 2.
 *
 * Two things this harness makes easy to get wrong, both learned the hard way:
 *
 * A plain `LazyColumn` of `BasicText` costs most of a frame here on its own, so a raw number
 * compared against the frame budget measures the harness and not the editor. [floor] is measured
 * and subtracted, the same way `TypingLatencyTest` does it.
 *
 * The editor is composed inside [DraftsTheme], as it is in the application. Outside one it builds
 * its own font families per text style, which is a cost the application does not pay.
 *
 * And the first sweep through the document is dominated by JIT: it starts around three times slower
 * than it ends. Comparing early samples against late ones without warming up says the editor slows
 * down as the reader scrolls, which is the opposite of what happens. [WARMUP_SWEEPS] full sweeps
 * are discarded before anything is measured.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollLatencyTest {
    @Test
    fun `scrolling a ten thousand word document does not cost an order more than an empty list`() {
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = EditorState(DocumentSession(gateDocument()))
            val scroll = LazyListState()
            setContent { DraftsTheme { BlockEditor(state = state, scroll = scroll) } }
            waitForIdle()

            repeat(WARMUP_SWEEPS) { sweep(scroll, state.blocks.size) }
            val steps = sweep(scroll, state.blocks.size)
            val median = steps.sorted()[steps.size / 2]
            val floor = floor()
            val attributable = median - floor

            println(
                """
                |Recurring gate -- scroll through the fixture
                |  document:        ${state.blocks.size} blocks
                |  screens:         ${steps.size}
                |  median:          $median microseconds per screen
                |  harness floor:   $floor microseconds (a plain list of the same length)
                |  attributable:    $attributable microseconds above the floor
                |  frame budget:    $FRAME_BUDGET microseconds at 120Hz
                |  verdict:         ${if (attributable < FRAME_BUDGET) "within budget" else "OVER"}
                """.trimMargin(),
            )

            // Deliberately loose, and not the gate.
            //
            // This runs inside a parallel Gradle build where several test JVMs compete for the
            // machine. Measured on a quiet one the editor costs about 1.7 times a plain list;
            // under `check` the same code measured 3.9, because the editor's work is more
            // CPU-bound than the floor's and contention does not hit them equally. A ratio does
            // not cancel that, so neither a microsecond budget nor a tight ratio can be asserted
            // here without flaking.
            //
            // So the assertion is a smoke test -- it catches a row that has started subcomposing
            // or lost its content cache, which costs an order of magnitude, and nothing subtler.
            // The real comparison is against the figures in PERFORMANCE.md, recorded from a quiet
            // run, which is what the plan's "regressions block" actually needs. That is a human
            // reading two numbers, the same way "slowest target device" is a human with a phone.
            assertTrue(
                median < floor * CATASTROPHE,
                "A screen of the editor cost ${median}us against a floor of ${floor}us",
            )
        }
    }

    @Test
    fun `scrolling costs the same at the end of the document as at the start`() {
        // The property that matters more than the absolute number: a `LazyColumn` composes only
        // what is visible, so scrolling into the end of a long document must cost what scrolling
        // into the start costs. A cache keyed on something that grows, or a lookup that walks the
        // block list, shows up here and nowhere else -- on a short document both ends are the same
        // place.
        //
        // Positional slices of an *unsorted* list. Sorting first and taking the ends compares the
        // fastest samples against the slowest, which is a different question with a much more
        // alarming answer.
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val state = EditorState(DocumentSession(gateDocument()))
            val scroll = LazyListState()
            setContent { DraftsTheme { BlockEditor(state = state, scroll = scroll) } }
            waitForIdle()
            repeat(WARMUP_SWEEPS) { sweep(scroll, state.blocks.size) }

            val steps = sweep(scroll, state.blocks.size)
            val quarter = steps.size / 4
            val nearTheTop = steps.take(quarter).sorted()[quarter / 2]
            val nearTheEnd = steps.takeLast(quarter).sorted()[quarter / 2]

            assertTrue(
                nearTheEnd < nearTheTop * DRIFT_ALLOWED,
                "Scrolling cost ${nearTheTop}us per screen near the top and ${nearTheEnd}us near the end",
            )
        }
    }

    /** One pass from the top to the bottom, a screen at a time, in document order. */
    private fun SkikoComposeUiTest.sweep(
        scroll: LazyListState,
        blocks: Int,
    ): List<Long> {
        val steps = mutableListOf<Long>()
        var index = 0

        runBlocking { scroll.scrollToItem(0) }
        waitForIdle()
        while (index < blocks - BLOCKS_PER_SCREEN) {
            index += BLOCKS_PER_SCREEN
            steps +=
                measureNanoTime {
                    runBlocking { scroll.scrollToItem(index) }
                    waitForIdle()
                } / NANOS_PER_MICRO
        }
        return steps
    }

    /**
     * The same sweep over a list that does nothing, so the harness's own cost can be subtracted.
     *
     * Composed in its own test body rather than beside the editor: two `setContent` calls in one
     * composition would have each measuring the other's recomposition.
     */
    private fun floor(): Long {
        var median = 0L
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT)) {
            val lines = List(FLOOR_ROWS) { "Paragraph $it with enough text in it to give the row a height." }
            val scroll = LazyListState()
            setContent {
                LazyColumn(state = scroll, modifier = Modifier.fillMaxWidth()) {
                    items(lines) { BasicText(it) }
                }
            }
            waitForIdle()

            repeat(WARMUP_SWEEPS) { sweep(scroll, lines.size) }
            val steps = sweep(scroll, lines.size)
            median = steps.sorted()[steps.size / 2]
        }
        return median
    }

    private companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 900f

        /** Roughly what fits in the window at the fixture's block heights. */
        const val BLOCKS_PER_SCREEN = 8

        /** The fixture's block count, so the floor sweeps the same distance. */
        const val FLOOR_ROWS = 512

        /** Enough for the first sweep's threefold JIT improvement to have happened. */
        const val WARMUP_SWEEPS = 2

        const val FRAME_BUDGET = 8_333L

        /**
         * The order-of-magnitude line. Measured at 1.7x quiet and 3.9x under a full build; eight
         * is past anything contention explains and squarely in the territory of a row that has
         * stopped being cheap.
         */
        const val CATASTROPHE = 8.0
        const val NANOS_PER_MICRO = 1_000L

        /** The end may cost a little more than the start; it must not cost a different order. */
        const val DRIFT_ALLOWED = 2.0
    }
}
