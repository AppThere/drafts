package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.open_outline
import com.appthere.drafts.i18n.resources.outline
import com.appthere.drafts.i18n.resources.outline_no_headings
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 10.1's outline, and 6's two shapes for it.
 *
 * "An outline view exposing the heading/scene hierarchy as a navigable list is disproportionately
 * valuable for screen reader users, who cannot skim."
 *
 * 6 decides where it goes: "Optional two-pane: document + outline" on an Expanded window, and a
 * sheet over the document on anything narrower.
 */
@OptIn(ExperimentalTestApi::class)
class OutlinePanelTest {
    @Test
    fun `the outline lists the document's headings`() {
        runSkikoComposeUiTest(size = DESKTOP) {
            openOutline()

            // Twice over for a heading the document is also showing: the outline's entry and the
            // document's own line. One node is enough to prove the outline lists it.
            assertTrue(onAllNodesWithText("Chapter one").fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodesWithText("Chapter nine").fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `on a wide window the outline is a pane beside the document`() {
        // 6: "Optional two-pane: document + outline". Beside, not over: the document gives up the
        // width rather than being covered by it.
        runSkikoComposeUiTest(size = DESKTOP) {
            val before = documentLeftEdge()
            openOutline()

            val pane = outlineBounds()
            val after = documentLeftEdge()

            assertTrue(
                after > pane.right.value - EDGE,
                "The document started at $after, under a pane ending at ${pane.right}",
            )
            assertTrue(after > before, "The document did not give up any width: $before then $after")
        }
    }

    @Test
    fun `on a phone the outline is a sheet along the bottom`() {
        // 6: "Outline and settings as modal sheets" in a Compact window.
        runSkikoComposeUiTest(size = PHONE) {
            openOutline()

            val pane = outlineBounds()

            assertTrue(pane.bottom.value >= PHONE.height - EDGE, "The sheet stopped at ${pane.bottom}")
            assertTrue(pane.left.value <= EDGE, "The sheet started at ${pane.left}")
        }
    }

    @Test
    fun `choosing a heading goes to it`() {
        // The whole point of a navigable list. The chosen heading is far enough down the document
        // to be off screen until the list scrolls there.
        runSkikoComposeUiTest(size = DESKTOP) {
            openOutline()

            // Before the click the only "Chapter nine" on screen is the outline's own entry: the
            // heading itself is seventy blocks down.
            onAllNodesWithText(LAST_HEADING)[0].performClick()
            waitForIdle()

            // The document is showing that heading *and* the caret is in it, which is what the
            // raw source says: a block with the caret in it reveals its markup (4.2).
            onNodeWithText(LAST_HEADING_SOURCE).assertIsDisplayed()
        }
    }

    @Test
    fun `a sheet closes when it has been used, and a pane does not`() {
        // A sheet is over the document, so it is in the way of arriving somewhere; a pane is not.
        runSkikoComposeUiTest(size = PHONE) {
            openOutline()
            onAllNodesWithText("Chapter one")[0].performClick()
            waitForIdle()

            assertTrue(outlineNodes().isEmpty(), "The sheet stayed open over the document")
        }

        runSkikoComposeUiTest(size = DESKTOP) {
            openOutline()
            onAllNodesWithText("Chapter one")[0].performClick()
            waitForIdle()

            assertTrue(outlineNodes().isNotEmpty(), "The pane closed itself")
        }
    }

    @Test
    fun `a document with no headings says so`() {
        runSkikoComposeUiTest(size = DESKTOP) {
            setContent { DraftsApp(initialText = "Just words.\n") }
            waitForIdle()
            onNodeWithContentDescription(words(Res.string.open_outline)).performClick()
            waitForIdle()

            onNodeWithText(words(Res.string.outline_no_headings)).assertIsDisplayed()
        }
    }

    /** Where the document's text begins, which is what the pane takes width from. */
    private fun SkikoComposeUiTest.documentLeftEdge(): Float {
        if (onAllNodesWithText(OPENING).fetchSemanticsNodes().isEmpty()) {
            setContent { DraftsApp(initialText = DOCUMENT) }
            waitForIdle()
        }
        return onNodeWithText(OPENING).getBoundsInRoot().left.value
    }

    private fun SkikoComposeUiTest.outlineBounds() = outlineNodes().single().let { onNode(pane).getBoundsInRoot() }

    private fun SkikoComposeUiTest.outlineNodes() = onAllNodes(pane).fetchSemanticsNodes()

    private val pane get() = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, words(Res.string.outline))

    private fun SkikoComposeUiTest.openOutline() {
        if (onAllNodesWithText(OPENING).fetchSemanticsNodes().isEmpty()) {
            setContent { DraftsApp(initialText = DOCUMENT) }
            waitForIdle()
        }
        onNodeWithContentDescription(words(Res.string.open_outline)).performClick()
        waitForIdle()
    }

    private companion object {
        val PHONE = Size(411f, 891f)
        val DESKTOP = Size(1400f, 1000f)
        const val EDGE = 4f

        const val OPENING = "An opening paragraph."
        const val LAST_HEADING = "Chapter nine"
        const val LAST_HEADING_SOURCE = "## Chapter nine"

        val NAMES = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

        val DOCUMENT =
            buildString {
                appendLine("# The salt road")
                appendLine()
                appendLine(OPENING)
                appendLine()
                (1..9).forEach { chapter ->
                    appendLine("## Chapter ${NAMES[chapter - 1]}")
                    appendLine()
                    appendLine("### A morning")
                    appendLine()
                    repeat(6) {
                        appendLine("Words and more words in chapter $chapter.")
                        appendLine()
                    }
                }
            }
    }
}
