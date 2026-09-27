package com.appthere.drafts.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The document at 100% and 200% system font scale.
 *
 * A Phase 3 acceptance criterion -- "Coherent at 100% and 200% system font scale" -- and 10.2 is
 * specific about why it should hold: "The 5 scale is ratio-based precisely so that a 200% system
 * scale produces a coherent document rather than a broken one. Test at 200%."
 *
 * *Coherent* is the word worth pinning down. It does not mean the page looks the same, which would
 * defeat the point: at 200% the text is twice the size and a line of it holds half as many words.
 * It means the relationships survive -- a heading stays proportionally a heading, the column stays
 * inside the window, and nothing is clipped or lost.
 */
@OptIn(ExperimentalTestApi::class)
class FontScaleTest {
    @Test
    fun `text is larger at two hundred percent`() {
        // The control on everything below: if the scale ignored the OS setting, every other
        // assertion here would pass while the reader saw no difference at all.
        val small = heightOf(BODY, scale = 1f)
        val large = heightOf(BODY, scale = 2f)

        assertTrue(large > small * GREW, "Body went from ${small}dp to ${large}dp, which is not twice")
    }

    @Test
    fun `the heading keeps its proportion to the body`() {
        // What "ratio-based" buys. H1 is 2.07x body at any scale, so the hierarchy a reader relies
        // on to skim is the same at 200% as at 100% -- rather than a headline that has outgrown its
        // page or a body that has caught up with it.
        val at100 = heightOf(HEADING, scale = 1f) / heightOf(BODY, scale = 1f)
        val at200 = heightOf(HEADING, scale = 2f) / heightOf(BODY, scale = 2f)

        assertTrue(
            abs(at100 - at200) < RATIO_TOLERANCE,
            "The heading is ${at100}x body at 100% and ${at200}x at 200%",
        )
    }

    @Test
    fun `the column stays inside the window at two hundred percent`() {
        // 5.3 clamps the column to the smaller of the measure and the window. At 200% the measure
        // doubles while the window does not, so the window has to win -- and if it does not, the
        // text runs off the edge where nobody can read it.
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT), density = Density(1f, fontScale = 2f)) {
            show()

            val bounds = onNodeWithText(BODY).getBoundsInRoot()

            assertTrue(bounds.left >= 0.dp, "The column starts off the left edge at ${bounds.left}")
            assertTrue(bounds.right.value <= WIDTH, "The column reaches ${bounds.right}, past the window")
        }
    }

    @Test
    fun `every block is still there at two hundred percent`() {
        // Coherent means nothing was lost making room. The last block of the document is the one
        // that would go missing if a layout had silently overflowed.
        runSkikoComposeUiTest(size = Size(WIDTH, TALL), density = Density(1f, fontScale = 2f)) {
            show()

            onNodeWithText(HEADING).assertExists()
            onNodeWithText(BODY).assertExists()
            onNodeWithText(LAST).assertExists()
        }
    }

    @Test
    fun `a reader at two hundred percent still gets a measure rather than the full window`() {
        // The column should be bound by *something* at every scale. At 200% on a wide window the
        // measure is 34em of 36dp text, which is still narrower than the window -- so the reader
        // gets a line they can track back from rather than one that spans the desk.
        runSkikoComposeUiTest(size = Size(VERY_WIDE, HEIGHT), density = Density(1f, fontScale = 2f)) {
            show()

            val width = onNodeWithText(BODY).getBoundsInRoot().let { it.right - it.left }

            assertTrue(width.value < VERY_WIDE * MOST_OF_THE_WINDOW, "The column is $width, nearly the window")
        }
    }

    private fun heightOf(
        text: String,
        scale: Float,
    ): Float {
        var height = 0f
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT), density = Density(1f, fontScale = scale)) {
            show()
            height = onNodeWithText(text).getBoundsInRoot().let { (it.bottom - it.top).value }
        }
        return height
    }

    private fun SkikoComposeUiTest.show() {
        val state = EditorState(DocumentSession("# $HEADING\n\n$BODY\n\n$LAST\n"))
        setContent { BlockEditor(state = state) }
    }

    private companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 900f
        const val TALL = 2400f
        const val VERY_WIDE = 4000f

        const val HEADING = "Chapter"
        const val BODY = "A short paragraph."
        const val LAST = "A final paragraph."

        /** Doubling the scale should do more than nudge the size. */
        const val GREW = 1.8f
        const val RATIO_TOLERANCE = 0.15f
        const val MOST_OF_THE_WINDOW = 0.8f
    }
}
