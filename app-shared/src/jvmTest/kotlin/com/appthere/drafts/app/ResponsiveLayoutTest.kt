package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.i18n.Strings
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 6, which had been computed and read nowhere.
 *
 * | Compact | < 600dp | "Bottom sheet for actions... Outline and settings as modal sheets." |
 * | Medium | 600-839dp | "Single document, centred column." |
 * | Expanded | >= 840dp | "Optional two-pane... Persistent rail." |
 *
 * `WindowSize` has been in the design system since Phase 3 with its own tests and no callers, so
 * every window got the same layout whatever its size. What is asserted here is the difference the
 * bands make, at sizes either side of a boundary the spec names.
 */
@OptIn(ExperimentalTestApi::class)
class ResponsiveLayoutTest {
    @Test
    fun `on a phone the settings arrive as a sheet along the bottom`() {
        // A phone held one-handed has its thumb at the bottom of the screen. A corner panel puts
        // every control at the far end of it.
        runSkikoComposeUiTest(size = PHONE) {
            openControls()

            val panel = panelBounds()

            assertTrue(panel.bottom.value >= PHONE.height - EDGE, "The sheet stopped at ${panel.bottom}")
            assertTrue(panel.left.value <= EDGE, "The sheet started at ${panel.left}")
            assertTrue(panel.right.value >= PHONE.width - EDGE, "The sheet ended at ${panel.right}")
        }
    }

    @Test
    fun `on a desktop window the settings stay beside the document`() {
        // There is room for a panel that does not cover the text. A sheet across the foot of a
        // wide window would be hiding a document for no reason.
        runSkikoComposeUiTest(size = DESKTOP) {
            openControls()

            val panel = panelBounds()

            assertTrue(panel.top.value < DESKTOP.height / 2, "The panel sat at ${panel.top}")
            assertTrue(panel.left.value > DESKTOP.width / 2, "The panel started at ${panel.left}")
        }
    }

    @Test
    fun `a tablet is not a phone`() {
        // 600dp is the boundary 6 names. A tablet in portrait is Medium, and gets the panel rather
        // than the sheet -- the case a test at phone and desktop sizes alone would miss.
        runSkikoComposeUiTest(size = TABLET) {
            openControls()

            val panel = panelBounds()

            assertTrue(panel.bottom.value < TABLET.height - EDGE, "The panel reached ${panel.bottom}")
        }
    }

    @Test
    fun `the sheet reaches both edges rather than being a panel pushed downwards`() {
        // A sheet spans the window. It does so because the placement drops the corner inset and
        // fills the width -- the panel's own width ceiling is wider than a phone, so it never
        // binds here and removing it for Compact was code that could not run.
        runSkikoComposeUiTest(size = PHONE) {
            openControls()

            val panel = panelBounds()
            val width = panel.right - panel.left

            assertTrue(width.value >= PHONE.width - EDGE * 2, "The sheet was $width of ${PHONE.width}px")
        }
    }

    /** The settings pane, found by the title assistive technology announces it with. */
    private fun SkikoComposeUiTest.panelBounds() =
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, Strings.READER_CONTROLS))
            .getBoundsInRoot()

    private fun SkikoComposeUiTest.openControls() {
        setContent { DraftsApp(initialText = "A paragraph.\n") }
        waitForIdle()

        onNodeWithContentDescription(Strings.OPEN_READER_CONTROLS).performClick()
        waitForIdle()
    }

    private companion object {
        /** Under 600dp: 6's Compact. */
        val PHONE = Size(411f, 891f)

        /** Between 600 and 839: 6's Medium. */
        val TABLET = Size(800f, 1200f)

        /** At or above 840: 6's Expanded. */
        val DESKTOP = Size(1400f, 1000f)

        /**
         * Tighter than the 16dp inset a corner panel carries, so these assertions can tell the two
         * apart. At 24 they could not: a phone's controls panel is tall enough to reach the bottom
         * of the screen wherever it is anchored, and 24 of slack swallowed the inset as well, so
         * anchoring every window to the corner passed all four tests.
         */
        const val EDGE = 4f
    }
}
