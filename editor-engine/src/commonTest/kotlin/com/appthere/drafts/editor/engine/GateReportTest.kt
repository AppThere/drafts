package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.SourceSpan
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Prints what the engine actually costs, for the gate report.
 *
 * The plan asks for the numbers, not a verdict: "the deliverable is knowledge." A passing assertion
 * says a bound holds; this says what the bound is.
 */
class GateReportTest {
    @Test
    fun `report bounded reparse on the gate fixture`() {
        val text = GateFixture.tenThousandWords()
        val session = DocumentSession(text)
        val blocks = session.blocks.size

        val target = session.blocks[blocks / 2].block.source!!
        val outcome = session.edit(SourceSpan.of(target.start.value, target.start.value), "x")

        println(
            """
            |
            |Phase 2 gate -- engine measurements
            |  document:        ${text.length} code units, $blocks blocks
            |  reparsed:        ${outcome.reparsed.length} code units (${percent(
                outcome.reparsed.length,
                text.length,
            )}% of the document)
            |  blocks rebuilt:  ${outcome.blocksReplaced}
            |  identities kept: ${outcome.blocksReused} of ${outcome.blocksReplaced} in the window
            |  blocks shifted:  ${outcome.blocksShifted}
            |  per-edit median: ${medianEditMicros(text)} microseconds
            |  frame budget:    8333 microseconds at 120Hz
            """.trimMargin(),
        )
    }

    /**
     * Median wall time of a keystroke, in microseconds.
     *
     * Median rather than mean, and a warm-up first: the gate cares whether typing keeps up, and one
     * unlucky garbage collection should not decide that. This measures the engine alone -- no
     * layout, no draw -- so it is a floor on the real cost, not an estimate of it.
     */
    private fun medianEditMicros(text: String): Long {
        val timings = mutableListOf<Long>()

        repeat(SAMPLES + WARMUP) { iteration ->
            val session = DocumentSession(text)
            val target = session.blocks[session.blocks.size / 2].block.source!!
            val at = SourceSpan.of(target.start.value, target.start.value)

            val started = TimeSource.Monotonic.markNow()
            session.edit(at, "x")
            val elapsed = started.elapsedNow().inWholeMicroseconds

            if (iteration >= WARMUP) timings.add(elapsed)
        }

        return timings.sorted()[timings.size / 2]
    }

    private companion object {
        const val WARMUP = 5
        const val SAMPLES = 21
    }

    private fun percent(
        part: Int,
        whole: Int,
    ): String {
        val tenths = if (whole == 0) 0 else part * 1000 / whole
        return "${tenths / 10}.${tenths % 10}"
    }
}
