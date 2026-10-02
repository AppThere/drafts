package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.document_state
import com.appthere.drafts.i18n.resources.state_clean
import com.appthere.drafts.i18n.resources.state_conflicted
import com.appthere.drafts.i18n.resources.state_dirty
import com.appthere.drafts.i18n.resources.state_orphaned
import com.appthere.drafts.i18n.resources.state_read_only
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
        assertShows(DocumentState.Dirty, words(Res.string.state_dirty))
        assertShows(DocumentState.Conflicted, words(Res.string.state_conflicted))
        assertShows(DocumentState.Orphaned, words(Res.string.state_orphaned))
        assertShows(DocumentState.ReadOnly, words(Res.string.state_read_only))
    }

    @Test
    fun `a clean document shows no label`() {
        runSkikoComposeUiTest(size = SIZE) {
            setContent { Badge(DocumentState.Clean) }

            // Unmerged, because the badge collapses to a single description for assistive
            // technology -- so *every* state has no visible text in the merged tree, and this
            // assertion would hold for all five without proving anything about any of them.
            onNodeWithText(words(Res.string.state_clean), useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `a clean document is still announced`() {
        // The label is dropped for the eye, not for the ear. Without a description the dot is an
        // unlabelled shape, and 10.2 asks for the state to be available to assistive technology --
        // which is the one audience for whom "no label" means "no information".
        runSkikoComposeUiTest(size = SIZE) {
            setContent { Badge(DocumentState.Clean) }

            onNodeWithContentDescription(
                "${words(Res.string.document_state)}, ${words(Res.string.state_clean)}",
            ).assertExists()
        }
    }

    @Test
    fun `the state reaches assistive technology as one description rather than a bare word`() {
        // A screen reader landing on "Unsaved" with nothing saying what is unsaved has been told
        // almost nothing. The badge merges into a single node so the noun arrives with the adjective.
        runSkikoComposeUiTest(size = SIZE) {
            setContent { Badge(DocumentState.Dirty) }

            onNodeWithContentDescription(
                "${words(Res.string.document_state)}, ${words(Res.string.state_dirty)}",
            ).assertExists()
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
