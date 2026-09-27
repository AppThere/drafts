package com.appthere.drafts.editor.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.engine.sourceOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The UI half of the Phase 2 gate.
 *
 * `IMPLEMENTATION-PLAN.md` asks for the reveal/preview behaviour to be *demonstrated*, and this is
 * where the demonstration is made repeatable. Clicking through the desktop app proves it once, on
 * one machine, until someone changes a span style; a test proves it on every build.
 *
 * The vertical-stability assertion is the one that matters most. `appthere-drafts.md` 4.1 says
 * block identity is stable and only inline decoration reveals, and 4.2 asks for that to be
 * measured rather than asserted by eye. Measuring the focused block's own height would not catch
 * the failure people actually notice: what they see is the rest of the document jumping. So the
 * measurement is taken on a *later* block, which moves if and only if the revealed one changed
 * height.
 */
@OptIn(ExperimentalTestApi::class)
class BlockEditorTest {
    @Test
    fun `an unfocused block hides its markup`() =
        runComposeUiTest {
            showDocument(DOCUMENT)

            onNodeWithText(PREVIEW).assertIsDisplayed()
        }

    @Test
    fun `clicking a block reveals its source`() =
        runComposeUiTest {
            showDocument(DOCUMENT)

            onNodeWithText(PREVIEW).performClick()

            onNodeWithText(REVEAL).assertIsDisplayed()
        }

    @Test
    fun `revealing a block does not move the blocks below it`() =
        runComposeUiTest {
            showDocument(DOCUMENT)
            val before = onNodeWithText(LAST).getBoundsInRoot().top

            onNodeWithText(PREVIEW).performClick()

            val after = onNodeWithText(LAST).getBoundsInRoot().top
            assertEquals(
                before,
                after,
                "The document moved by ${after - before} when a block revealed. 4.1 requires " +
                    "block metrics to be identical in both states.",
            )
        }

    @Test
    fun `a heading keeps its size when revealed`() =
        runComposeUiTest {
            showDocument(DOCUMENT)
            val before = onNodeWithText(HEADING_PREVIEW).getBoundsInRoot().let { it.bottom - it.top }

            onNodeWithText(HEADING_PREVIEW).performClick()

            val after = onNodeWithText(HEADING_REVEAL).getBoundsInRoot().let { it.bottom - it.top }
            assertEquals(before, after, "The heading changed size on focus")
        }

    /** The editor over a live session, which is what `:app-shared` composes. */
    @Test
    fun `a revealed block is laid out at the same width as its preview`() {
        // Width is upstream of everything 4.2 asks for: a field narrower than the preview it
        // replaced wraps to more lines, and more lines move the whole document down. Cheaper to
        // assert directly than to diagnose from the symptom.
        runComposeUiTest {
            val state = showDocument(DOCUMENT)
            val before = onNodeWithText(PREVIEW).getBoundsInRoot().let { it.right - it.left }

            onNodeWithText(PREVIEW).performClick()

            val after = onNodeWithText(REVEAL).getBoundsInRoot().let { it.right - it.left }
            assertEquals(before, after, "The field is not the width of the preview it replaced")
        }
    }

    private fun ComposeUiTest.showDocument(text: String): EditorState {
        val state = EditorState(DocumentSession(text))
        setContent { BlockEditor(state = state) }
        return state
    }

    private companion object {
        const val DOCUMENT = "## A heading\n\nThis has *emphasis* in it.\n\nA final paragraph.\n"

        const val HEADING_PREVIEW = "A heading"
        const val HEADING_REVEAL = "## A heading"
        const val PREVIEW = "This has emphasis in it."
        const val REVEAL = "This has *emphasis* in it."
        const val LAST = "A final paragraph."
    }
}
