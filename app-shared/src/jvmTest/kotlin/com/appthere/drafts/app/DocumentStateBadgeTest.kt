package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.DocumentState
import kotlin.test.Test

/**
 * 8.4's badge on screen.
 *
 * "Surface the state in the window chrome -- quietly, as a dot or a short label, not a dialog." The
 * two things worth asserting are that each state says something a reader can act on, and that
 * `clean` says nothing at all -- a permanent "Saved" is chrome that stops being read.
 */
@OptIn(ExperimentalTestApi::class)
class DocumentStateBadgeTest {
    @Test
    fun `each state that needs a word shows one`() {
        assertShows(DocumentState.Dirty, Strings.STATE_DIRTY)
        assertShows(DocumentState.Conflicted, Strings.STATE_CONFLICTED)
        assertShows(DocumentState.Orphaned, Strings.STATE_ORPHANED)
        assertShows(DocumentState.ReadOnly, Strings.STATE_READ_ONLY)
    }

    @Test
    fun `a clean document shows no label`() {
        runSkikoComposeUiTest(size = SIZE) {
            setContent { Badge(DocumentState.Clean) }

            // Unmerged, because the badge collapses to a single description for assistive
            // technology -- so *every* state has no visible text in the merged tree, and this
            // assertion would hold for all five without proving anything about any of them.
            onNodeWithText(Strings.STATE_CLEAN, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `a clean document is still announced`() {
        // The label is dropped for the eye, not for the ear. Without a description the dot is an
        // unlabelled shape, and 10.2 asks for the state to be available to assistive technology --
        // which is the one audience for whom "no label" means "no information".
        runSkikoComposeUiTest(size = SIZE) {
            setContent { Badge(DocumentState.Clean) }

            onNodeWithContentDescription("${Strings.DOCUMENT_STATE}, ${Strings.STATE_CLEAN}").assertExists()
        }
    }

    @Test
    fun `the state reaches assistive technology as one description rather than a bare word`() {
        // A screen reader landing on "Unsaved" with nothing saying what is unsaved has been told
        // almost nothing. The badge merges into a single node so the noun arrives with the adjective.
        runSkikoComposeUiTest(size = SIZE) {
            setContent { Badge(DocumentState.Dirty) }

            onNodeWithContentDescription("${Strings.DOCUMENT_STATE}, ${Strings.STATE_DIRTY}").assertExists()
        }
    }

    private fun assertShows(
        state: DocumentState,
        label: String,
    ) {
        runSkikoComposeUiTest(size = SIZE) {
            setContent { Badge(state) }

            onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @androidx.compose.runtime.Composable
    private fun Badge(state: DocumentState) {
        DraftsTheme(ReaderSettings()) { DocumentStateBadge(state) }
    }

    private companion object {
        val SIZE = Size(400f, 200f)
    }
}
