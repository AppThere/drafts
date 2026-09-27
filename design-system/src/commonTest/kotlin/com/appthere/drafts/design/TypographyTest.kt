package com.appthere.drafts.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The prose scale and the measure, checked against the numbers `appthere-drafts.md` 5 prints.
 *
 * The spec quotes both the ratios and the sizes they produce at the 18sp default, which makes it
 * possible to check the transcription rather than merely that it compiles. A scale that has drifted
 * from the document is worse than one that was never written down, because everyone will keep
 * reading the document.
 */
class TypographyTest {
    @Test
    fun `the scale produces the sizes the spec quotes at the default base`() {
        // 5.2 gives both columns: "H1 | 2.07x (37sp)". Rounded, because the spec rounds.
        val expected =
            listOf(
                Prose.H1 to 37,
                Prose.H2 to 31,
                Prose.H3 to 26,
                Prose.H4 to 22,
                Prose.H5 to 20,
                Prose.H6 to 18,
                Prose.Body to 18,
            )

        expected.forEach { (role, size) ->
            val actual = role.sizeAt(Prose.DefaultBase).value
            assertTrue(
                abs(actual - size) < ROUNDING,
                "Expected about ${size}sp at the default base, got $actual",
            )
        }
    }

    @Test
    fun `the scale is a scale`() {
        // Monotonic through the headings, and nothing below body except the two roles meant to be.
        val headings = (1..HEADING_LEVELS).map { Prose.heading(it).ratio }

        assertEquals(headings.sortedDescending(), headings, "The headings do not descend in size")
        assertTrue(Prose.Code.ratio < Prose.Body.ratio, "Code should be a touch smaller than body")
        assertTrue(Prose.Caption.ratio < Prose.Code.ratio, "Captions should be the smallest role")
    }

    @Test
    fun `body line height is the 1_6 the spec insists on`() {
        assertEquals(1.6f, Prose.Body.lineHeight)
        assertEquals(1.6f, Prose.Quote.lineHeight)
    }

    @Test
    fun `every role has a line height that leaves the text room`() {
        // A line height below 1.2 sets solid or tighter, which is unreadable at length whatever the
        // size. The headings are the tightest by design and 1.2 is the floor.
        Prose.all.forEach { role ->
            assertTrue(role.lineHeight >= MINIMUM_LINE_HEIGHT, "A role has line height ${role.lineHeight}")
        }
    }

    @Test
    fun `heading levels are clamped to what Markdown can express`() {
        assertEquals(Prose.H1, Prose.heading(1))
        assertEquals(Prose.H6, Prose.heading(HEADING_LEVELS))

        // Nothing outside 1..6 can be parsed, but the scale should not throw if something tries.
        assertEquals(Prose.H1, Prose.heading(0))
        assertEquals(Prose.H6, Prose.heading(HEADING_LEVELS + 1))
    }

    @Test
    fun `the base size is held to the range the reader controls allow`() {
        assertEquals(Prose.MinimumBase, Prose.clampBase(2.sp))
        assertEquals(Prose.MaximumBase, Prose.clampBase(200.sp))
        assertEquals(20.sp, Prose.clampBase(20.sp))
    }

    @Test
    fun `a wide window gets the 34em measure and keeps the rest as margin`() {
        // 5.3: "At the 18sp default this is roughly 612dp".
        val measured = Measure.of(availableWidth = 1600.dp, bodySize = 18.dp)

        assertTrue(
            abs(measured.contentWidth.value - EXPECTED_MEASURE) < ROUNDING,
            "Expected about ${EXPECTED_MEASURE}dp of measure, got ${measured.contentWidth}",
        )
    }

    @Test
    fun `a narrow window fills what it has minus gutters`() {
        // 5.3: "with a floor so that on narrow screens it simply fills available width minus
        // gutters". A phone never reaches 34em, so the measure never binds.
        val width = 360.dp
        val measured = Measure.of(availableWidth = width, bodySize = 18.dp)

        assertEquals(width - measured.gutter * 2, measured.contentWidth)
    }

    @Test
    fun `the gutter is proportional but never below its floor`() {
        assertEquals(Measure.MinimumGutter, Measure.of(320.dp, 18.dp).gutter, "A narrow window takes the floor")

        val wide = Measure.of(1600.dp, 18.dp)
        assertEquals(1600.dp * Measure.GUTTER_FRACTION, wide.gutter, "A wide window takes the percentage")
    }

    @Test
    fun `the measure grows with the base size rather than staying put`() {
        // The point of a ratio-based scale: a reader at 28sp gets a wider column, not more
        // characters crammed into the same one. This is what makes 200% system scale coherent.
        val small = Measure.of(2000.dp, 14.dp).contentWidth
        val large = Measure.of(2000.dp, 28.dp).contentWidth

        assertTrue(large > small, "A larger base should widen the column, got $small then $large")
        assertEquals(small.value * 2, large.value, ROUNDING)
    }

    @Test
    fun `the column never exceeds the window`() {
        listOf(200, 360, 600, 840, 1200, 3840).forEach { width ->
            val measured = Measure.of(width.dp, 28.dp)
            assertTrue(
                measured.contentWidth + measured.gutter * 2 <= width.dp,
                "At ${width}dp the column and gutters come to more than the window",
            )
        }
    }

    private companion object {
        const val ROUNDING = 1f
        const val HEADING_LEVELS = 6
        const val MINIMUM_LINE_HEIGHT = 1.2f

        /** 34em at 18dp: the ~612dp the spec quotes. */
        const val EXPECTED_MEASURE = 612f
    }
}
