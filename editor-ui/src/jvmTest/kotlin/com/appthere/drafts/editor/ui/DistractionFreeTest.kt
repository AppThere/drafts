package com.appthere.drafts.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.FocusMode
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 12's two reading options.
 *
 * "**Typewriter scrolling** as an option: keep the caret at a fixed vertical position."
 * "**Focus mode** as an option: dim all blocks except the current one."
 *
 * Both are options, and the half of each worth testing hardest is that they are *off* by default.
 * A document that dims itself or moves under the reader without being asked is the kind of thing
 * that gets an editor uninstalled.
 */
@OptIn(ExperimentalTestApi::class)
class DistractionFreeTest {
    @Test
    fun `nothing is dimmed by default`() {
        // Focus mode is offered, not imposed.
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(ReaderSettings())
            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            onNodeWithText(SECOND).assertIsDisplayed()
        }
    }

    @Test
    fun `focus mode leaves the caret's own block alone`() {
        // The one block that must stay at full contrast is the one being written in.
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(ReaderSettings(focusMode = FocusMode.Block))
            state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            onNodeWithText(FIRST).assertIsDisplayed()
        }
    }

    @Test
    fun `focus mode dims the others without removing them`() {
        // 12 says dim, not hide. A reader needs to see how far through a chapter they are, and a
        // mode that removed the rest of the document would be a different feature.
        //
        // Measured off the rendered pixels, because an alpha is not in the semantics tree: a test
        // that only asked whether the node still existed would pass with focus mode doing nothing
        // at all.
        val bright = darkestOf(ReaderSettings())
        val dimmed = darkestOf(ReaderSettings(focusMode = FocusMode.Block))
        val background = darkestOf(ReaderSettings(), region = Region.Blank)

        assertTrue(dimmed > bright + FADED, "A dimmed block reached $dimmed against $bright undimmed")
        assertTrue(dimmed < background - STILL_THERE, "The dimmed block faded into the background at $dimmed")
    }

    private enum class Region { SecondBlock, Blank }

    /**
     * The darkest pixel in a region, with the caret in the first block.
     *
     * The darkest pixel and not the average. Text covers a small fraction of a row -- the rest is
     * background -- so an average over the row is an average of the background, and the first
     * version of this test reported dimmed and undimmed as within a tenth of a percent of each
     * other while focus mode was working perfectly well.
     */
    private fun darkestOf(
        settings: ReaderSettings,
        region: Region = Region.SecondBlock,
        placeCaret: Boolean = true,
    ): Float {
        var darkest = 1f
        runSkikoComposeUiTest(size = SIZE) {
            val state = show(settings)
            if (placeCaret) state.place(Caret(state.blocks.first().id, 0))
            waitForIdle()

            val bounds = onNodeWithText(SECOND).getBoundsInRoot()
            val image = onRoot().captureToImage().toPixelMap()
            val rows =
                when (region) {
                    // A strip of the gutter, which no text reaches, to find out what "nothing" is.
                    Region.Blank -> {
                        0 until 1
                    }

                    Region.SecondBlock -> {
                        bounds.top.value
                            .toInt()
                            .coerceAtLeast(0)..bounds.bottom.value
                            .toInt()
                            .coerceAtMost(image.height - 1)
                    }
                }

            for (y in rows) {
                for (x in 0 until image.width) {
                    val pixel = image[x, y]
                    darkest = minOf(darkest, (pixel.red + pixel.green + pixel.blue) / CHANNELS)
                }
            }
        }
        return darkest
    }

    @Test
    fun `nothing is dimmed before anything has the caret`() {
        // Every launch, before the reader has clicked. With no current block there is nothing to
        // focus on, and dimming everything would leave a uniformly faint document.
        //
        // Measured, not merely asserted to exist: an earlier version of this checked the blocks
        // were displayed, which stays true however faint they are, and passed happily against an
        // implementation that dimmed the whole document.
        val untouched = darkestOf(ReaderSettings(focusMode = FocusMode.Block), placeCaret = false)
        val plain = darkestOf(ReaderSettings(), placeCaret = false)

        assertTrue(untouched < plain + SAME, "An untouched document was dimmed to $untouched against $plain")
    }

    @Test
    fun `the document does not move under the reader by default`() {
        // Typewriter scrolling off. Placing the caret in a block far down must not scroll the
        // document -- the reader put it there deliberately and the view should stay where it was.
        runSkikoComposeUiTest(size = SIZE) {
            val scroll = LazyListState()
            val state = show(ReaderSettings(), scroll)
            waitForIdle()

            state.place(Caret(state.blocks[TARGET].id, 0))
            waitForIdle()

            assertEquals(0, scroll.firstVisibleItemIndex, "The document scrolled without being asked")
        }
    }

    @Test
    fun `typewriter scrolling brings the caret's block to the line`() {
        runSkikoComposeUiTest(size = SIZE) {
            val scroll = LazyListState()
            val state = show(ReaderSettings(typewriterScrolling = true), scroll)
            waitForIdle()

            state.place(Caret(state.blocks[TARGET].id, 0))
            waitForIdle()

            assertTrue(scroll.firstVisibleItemIndex > 0, "The document did not move to follow the caret")
        }
    }

    @Test
    fun `the typewriter line is above the middle of the window`() {
        // 12 says "a fixed vertical position" and leaves the position open. Above the middle:
        // what a writer needs to see is the sentence they just finished and the shape of the
        // paragraph it belongs to, which is above the caret rather than below it.
        runSkikoComposeUiTest(size = SIZE) {
            val scroll = LazyListState()
            val state = show(ReaderSettings(typewriterScrolling = true), scroll)
            waitForIdle()

            state.place(Caret(state.blocks[TARGET].id, 0))
            waitForIdle()

            val top = onNodeWithText(blockText(TARGET)).getBoundsInRoot().top.value
            assertTrue(
                abs(top - HEIGHT * EXPECTED_LINE) < HEIGHT * TOLERANCE,
                "The caret's block sat at ${top}px of a ${HEIGHT}px window",
            )
        }
    }

    private fun SkikoComposeUiTest.show(
        settings: ReaderSettings,
        scroll: LazyListState = LazyListState(),
    ): EditorState {
        val state = EditorState(DocumentSession(document()))
        setContent {
            DraftsTheme(settings) {
                // The page, which `DraftsApp` paints and this host otherwise would not. Without it
                // the surface is transparent, every unpainted pixel captures as black, and any
                // measurement of "the darkest pixel" is a measurement of nothing being there.
                Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
                    BlockEditor(state = state, scroll = scroll)
                }
            }
        }
        return state
    }

    private fun document() = (0 until BLOCKS).joinToString("\n\n") { blockText(it) } + "\n"

    private fun blockText(index: Int) =
        when (index) {
            0 -> FIRST
            1 -> SECOND
            BLOCKS - 1 -> LAST
            else -> "Paragraph number $index of the document."
        }

    private companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 800f
        val SIZE = Size(WIDTH, HEIGHT)

        const val BLOCKS = 40

        /** Far enough down that reaching it has to scroll. */
        const val TARGET = 25

        const val FIRST = "The first paragraph."
        const val SECOND = "The second paragraph."
        const val LAST = "The final paragraph."

        /** `TYPEWRITER_LINE` in `BlockEditor`, which this asserts rather than reads. */
        const val EXPECTED_LINE = 0.4f

        /** A dimmed row should visibly fade, not lose a sliver of contrast. */
        const val FADED = 0.2f

        /** Two renderings of the same undimmed text differ by rounding, not by this much. */
        const val SAME = 0.02f

        /** And should still be visibly there against the page. */
        const val STILL_THERE = 0.05f
        const val CHANNELS = 3f
        const val TOLERANCE = 0.12f
    }
}
