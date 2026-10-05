package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.editor.engine.BlockParser
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.heading_offer
import com.appthere.drafts.i18n.resources.heading_offer_mark
import com.appthere.drafts.i18n.resources.heading_offer_not_now
import com.appthere.drafts.i18n.resources.scene_headings_not_saved
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 11.3: "the app should offer to insert [forcing characters] automatically when it detects an
 * unrecognised heading pattern" -- and, chosen with the product owner, to add the word instead.
 */
@OptIn(ExperimentalTestApi::class)
class HeadingOfferTest {
    @Test
    fun `leaving a line that looks like a heading offers to make it one`() =
        runSkikoComposeUiTest(size = SIZE) {
            val editor = show(FakeDocumentStore(SCRIPT, TEXT))

            visit(editor, 0)
            assertFalse(offering(), "Offered while the line was still being written")

            visit(editor, 1)
            assertTrue(offering(), "Nothing was offered for the line just left")
        }

    @Test
    fun `mark it forces the line with a full stop that undo takes back`() =
        runSkikoComposeUiTest(size = SIZE) {
            val editor = show(FakeDocumentStore(SCRIPT, TEXT))
            leaveHeading(editor)

            onNodeWithContentDescription(words(Res.string.heading_offer_mark)).performClick()
            waitForIdle()

            assertEquals(".$TEXT", editor.text)
            assertEquals(
                BlockRole.SCENE_HEADING,
                editor.blocks
                    .first()
                    .block.role,
            )
            assertFalse(offering())

            editor.undo()
            assertEquals(TEXT, editor.text)
        }

    @Test
    fun `adding the word reads every such line as a heading and keeps it`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(SCRIPT, "$TEXT\nEXTÉRIEUR RUE - NUIT\n\nIl sort.\n")
            val editor = show(store)
            leaveHeading(editor)

            onNodeWithContentDescription("Add “INTÉRIEUR”").performClick()
            waitForIdle()

            assertEquals(
                BlockRole.SCENE_HEADING,
                editor.blocks
                    .first()
                    .block.role,
            )
            assertTrue("INTÉRIEUR" in editor.keywords!!.sceneHeadingPrefixes)
            assertTrue(editor.text.startsWith("INTÉRIEUR"), "Adding the word changed the text")
            val kept = runBlocking { SettingsStore(store, SETTINGS).keywordsFor(IDENTITY) }
            assertTrue("INTÉRIEUR" in kept.sceneHeadingPrefixes)
        }

    @Test
    fun `not now is not asked again for that line`() =
        runSkikoComposeUiTest(size = SIZE) {
            val editor = show(FakeDocumentStore(SCRIPT, TEXT))
            leaveHeading(editor)

            onNodeWithContentDescription(words(Res.string.heading_offer_not_now)).performClick()
            waitForIdle()
            assertFalse(offering())

            leaveHeading(editor)
            assertFalse(offering(), "Asked again about a line the reader said not now to")
        }

    @Test
    fun `a heading the words already know is not offered`() =
        runSkikoComposeUiTest(size = SIZE) {
            val editor = show(FakeDocumentStore(SCRIPT, "INT. KITCHEN - DAY\n\nShe enters.\n"))
            leaveHeading(editor)

            assertFalse(offering())
        }

    @Test
    fun `a word that could not be kept is said and still applies`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(SCRIPT, TEXT)
            val editor = show(store)
            store.failsToWrite = true
            leaveHeading(editor)

            onNodeWithContentDescription("Add “INTÉRIEUR”").performClick()

            waitUntil(timeoutMillis = TIMEOUT) {
                onAllNodesWithText(words(Res.string.scene_headings_not_saved)).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(
                BlockRole.SCENE_HEADING,
                editor.blocks
                    .first()
                    .block.role,
            )
        }

    private fun SkikoComposeUiTest.show(store: FakeDocumentStore): EditorState {
        val document =
            runBlocking {
                val contents = store.read(SCRIPT)
                OpenDocument(
                    store = store,
                    editor = EditorState(DocumentSession(contents.text, BlockParser.Fountain())),
                    opened = DocumentSessionState.opened(SCRIPT, contents),
                )
            }
        val keeper = SnapshotKeeper(document, SnapshotStore(store, "/sessions"), IDENTITY)

        setContent {
            DraftsApp(document = document, keeper = keeper, settingsStore = SettingsStore(store, SETTINGS))
        }
        waitForIdle()
        return document.editor
    }

    /** The caret into the heading line, and out again. */
    private fun SkikoComposeUiTest.leaveHeading(editor: EditorState) {
        visit(editor, 0)
        visit(editor, 1)
    }

    private fun SkikoComposeUiTest.visit(
        editor: EditorState,
        index: Int,
    ) {
        editor.place(Caret(editor.blocks[index].id, 0))
        waitForIdle()
    }

    private fun SkikoComposeUiTest.offering(): Boolean =
        onAllNodesWithText(words(Res.string.heading_offer)).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L

        val SCRIPT = DocumentRef("/scripts/cuisine.fountain")
        const val TEXT = "INTÉRIEUR CUISINE - JOUR\n\nElle entre.\n"
        const val SETTINGS = "/settings"

        val IDENTITY =
            SessionIdentity(
                documentId = "cuisine",
                uri = "file:///scripts/cuisine.fountain",
                displayName = "cuisine.fountain",
                kind = "fountain",
            )
    }
}
