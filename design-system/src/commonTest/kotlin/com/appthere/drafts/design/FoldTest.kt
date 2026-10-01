package com.appthere.drafts.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 6's hinge rule: "never let the fold bisect the measure".
 *
 * The arithmetic is here rather than in a composition test because it is arithmetic, and because
 * the thing worth asserting is a property -- whatever the hinge's position and width, no part of it
 * is left inside the band the content gets. A test of the numbers alone would agree with a version
 * that cleared the wrong side, since clearing *a* side is half the answer.
 */
class FoldTest {
    @Test
    fun `a window with no fold is left alone`() {
        assertEquals(FoldClearance.None, null.clearanceWithin(DESKTOP))
    }

    @Test
    fun `a foldable lying flat keeps the whole display`() {
        // `isSeparating` false is the platform saying these are not two logical halves. Taking half
        // the display away from a reader for a crease they can see through would be a regression
        // dressed as 6 compliance.
        val flat = Fold(FoldAxis.Vertical, HINGE, HINGE, isSeparating = false)

        assertEquals(FoldClearance.None, flat.clearanceWithin(INNER_DISPLAY))
    }

    @Test
    fun `a horizontal hinge does not bisect the measure`() {
        // It cuts the window top from bottom. A line of text crosses it at a point rather than
        // along its length, and confining a scrolling document to half the height would cost more
        // than it buys. Recorded in divergences.md.
        val sideways = Fold(FoldAxis.Horizontal, 400.dp, 400.dp, isSeparating = true)

        assertEquals(FoldClearance.None, sideways.clearanceWithin(INNER_DISPLAY))
    }

    @Test
    fun `the content takes the wider side of the hinge`() {
        // The Pixel Fold's inner display: 841dp wide with the hinge 411dp in, so the right-hand
        // half is the larger by 18dp and is the one the measure gets.
        val fold = Fold(FoldAxis.Vertical, HINGE, HINGE, isSeparating = true)

        val clearance = fold.clearanceWithin(INNER_DISPLAY)

        assertEquals(HINGE, clearance.start)
        assertEquals(0.dp, clearance.end)
    }

    @Test
    fun `a hinge past the middle sends the content to the near side`() {
        // The mirror of the case above, and the one a test of the Pixel Fold alone would miss:
        // clearing the start side unconditionally passes that test and fails this one.
        val fold = Fold(FoldAxis.Vertical, 600.dp, 600.dp, isSeparating = true)

        val clearance = fold.clearanceWithin(INNER_DISPLAY)

        assertEquals(0.dp, clearance.start)
        assertEquals(INNER_DISPLAY - 600.dp, clearance.end)
    }

    @Test
    fun `a hinge with width is cleared along with the side behind it`() {
        // A gap rather than a crease. Clearing up to the hinge's near edge would leave the content
        // under it.
        val fold = Fold(FoldAxis.Vertical, 300.dp, 340.dp, isSeparating = true)

        assertEquals(340.dp, fold.clearanceWithin(INNER_DISPLAY).start)
    }

    @Test
    fun `no hinge is ever left inside the band the content gets`() {
        // The property, over every geometry above and a few degenerate ones: a hinge exactly in the
        // middle, one at each edge, and one wider than the window.
        val hinges =
            listOf(
                HINGE to HINGE,
                600.dp to 600.dp,
                300.dp to 340.dp,
                INNER_DISPLAY / 2 to INNER_DISPLAY / 2,
                0.dp to 0.dp,
                INNER_DISPLAY to INNER_DISPLAY,
                (-10).dp to (INNER_DISPLAY + 10.dp),
            )

        hinges.forEach { (start, end) ->
            val fold = Fold(FoldAxis.Vertical, start, end, isSeparating = true)
            val clearance = fold.clearanceWithin(INNER_DISPLAY)
            val bandStart = clearance.start
            val bandEnd = INNER_DISPLAY - clearance.end

            // Touching the hinge's edge is allowed; overlapping it is not. A band with nothing
            // in it -- which is what a hinge wider than the window leaves -- contains no hinge.
            val overlaps = bandStart < bandEnd && bandStart < end && bandEnd > start
            assertTrue(
                !overlaps,
                "A hinge at $start..$end was left inside the content band $bandStart..$bandEnd",
            )
        }
    }

    @Test
    fun `a hinge in the exact middle still picks a side`() {
        // Neither half is wider. The answer has to be one of them rather than neither, or the
        // measure stays bisected on a device that folds down the centre.
        val fold = Fold(FoldAxis.Vertical, INNER_DISPLAY / 2, INNER_DISPLAY / 2, isSeparating = true)

        assertTrue(!fold.clearanceWithin(INNER_DISPLAY).isEmpty, "A centred hinge cleared nothing")
    }

    private companion object {
        /** The Pixel Fold's inner display at 420dpi: 2208px wide, and 2208/2.625 is this. */
        val INNER_DISPLAY: Dp = 841.1.dp

        /** Its hinge, a zero-width line at 1080px. */
        val HINGE: Dp = 411.4.dp

        val DESKTOP: Dp = 1400.dp
    }
}
