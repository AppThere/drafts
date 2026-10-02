package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.Fold
import com.appthere.drafts.design.FoldAxis
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.open_reader_controls
import com.appthere.drafts.i18n.resources.reader_controls
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 6 on the device it was written for: "never let the fold bisect the measure".
 *
 * The geometry is the Pixel Fold's inner display, which is what the `pixel_fold` AVD reports: 841dp
 * wide with a zero-width hinge 411dp in, a little left of centre. `FoldTest` checks the arithmetic;
 * this checks that the text ends up where the arithmetic says, which is a different question -- the
 * clearance has to reach the editor, and the editor has to still centre its measure inside what it
 * is given rather than inside the window.
 *
 * The flat case is here for the same reason a mutation test is: without it, a layout that moved the
 * column off half the display whatever the posture would pass.
 */
@OptIn(ExperimentalTestApi::class)
class FoldLayoutTest {
    @Test
    fun `a book-posture fold puts the whole measure on one side of the hinge`() {
        runSkikoComposeUiTest(size = INNER_DISPLAY) {
            openWith(BOOK_POSTURE)

            val text = onNodeWithText(PARAGRAPH).getBoundsInRoot()

            val clears = text.left.value >= HINGE || text.right.value <= HINGE
            assertTrue(clears, "The measure ran from ${text.left} to ${text.right} across a hinge at $HINGE")
        }
    }

    @Test
    fun `it takes the wider half, which on this device is the right`() {
        // The hinge sits 411dp into an 841dp window, so there is 430dp to its right and 411 to its
        // left. Choosing the narrower half would still satisfy the test above.
        runSkikoComposeUiTest(size = INNER_DISPLAY) {
            openWith(BOOK_POSTURE)

            val text = onNodeWithText(PARAGRAPH).getBoundsInRoot()

            assertTrue(text.left.value >= HINGE, "The measure started at ${text.left}, left of the hinge")
        }
    }

    @Test
    fun `a foldable lying flat keeps the whole display`() {
        // `isSeparating` false. The display is one continuous surface and the measure spans it --
        // which is also the evidence that the test above is measuring the fold's effect and not
        // some constant inset.
        runSkikoComposeUiTest(size = INNER_DISPLAY) {
            openWith(BOOK_POSTURE.copy(isSeparating = false))

            val text = onNodeWithText(PARAGRAPH).getBoundsInRoot()

            assertTrue(
                text.left.value < HINGE && text.right.value > HINGE,
                "A flat display gave the measure ${text.left}..${text.right}, off to one side",
            )
        }
    }

    @Test
    fun `a window with no fold is centred as before`() {
        runSkikoComposeUiTest(size = INNER_DISPLAY) {
            openWith(null)

            val text = onNodeWithText(PARAGRAPH).getBoundsInRoot()
            val leftMargin = text.left.value
            val rightMargin = INNER_DISPLAY.width - text.right.value

            assertTrue(
                kotlin.math.abs(leftMargin - rightMargin) < EDGE,
                "The measure sat at ${text.left}..${text.right} in a ${INNER_DISPLAY.width}px window",
            )
        }
    }

    @Test
    fun `the half the page gets is what decides 6's band`() {
        // 841dp of glass, 411 of page. 6's table is about what layout fits, so the settings arrive
        // as a Compact bottom sheet inside that half rather than as the Expanded corner panel the
        // unfolded device would get -- and the sheet still stops at the hinge.
        runSkikoComposeUiTest(size = INNER_DISPLAY) {
            openWith(BOOK_POSTURE)
            onNodeWithContentDescription(words(Res.string.open_reader_controls)).performClick()
            waitForIdle()

            val panel =
                onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, words(Res.string.reader_controls)))
                    .getBoundsInRoot()

            assertTrue(
                panel.bottom.value >= INNER_DISPLAY.height - EDGE,
                "The settings stopped at ${panel.bottom}, so they were a corner panel",
            )
            // Less the rounding: the hinge is at 411.4dp and layout lands on whole pixels.
            assertTrue(
                panel.left.value >= HINGE - EDGE,
                "The sheet started at ${panel.left}, across the hinge",
            )
        }
    }

    private fun SkikoComposeUiTest.openWith(fold: Fold?) {
        setContent { DraftsApp(initialText = "$PARAGRAPH\n", fold = fold) }
        waitForIdle()
    }

    private companion object {
        /** The Pixel Fold's inner display: 2208x1840 at 420dpi. Test sizes are in Dp. */
        val INNER_DISPLAY = Size(841f, 700f)

        /** Its hinge, a zero-width vertical line at 1080px. */
        const val HINGE = 411.4f

        val BOOK_POSTURE = Fold(FoldAxis.Vertical, HINGE.dp, HINGE.dp, isSeparating = true)

        /**
         * Long enough to wrap, so the text node is as wide as the column it is in. A short
         * paragraph's node is as wide as the words, which would say nothing about the measure.
         */
        const val PARAGRAPH =
            "The hinge runs down the middle of the inner display, and a line of text that " +
                "crosses it is a line the reader has to follow across a crease. This paragraph " +
                "is long enough to wrap several times at any measure the device can give it."

        /** A couple of pixels, for rounding in a centred layout. */
        const val EDGE = 2f
    }
}
