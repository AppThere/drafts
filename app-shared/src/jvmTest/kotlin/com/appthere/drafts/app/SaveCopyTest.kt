package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.conflict
import com.appthere.drafts.i18n.resources.save_copy
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.DocumentState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 8.2's "[ Save a copy… ]": both versions kept, the reader's in a new file and the other untouched.
 *
 * A real refusal -- the file changed on disk after the document opened -- answered with a copy saved
 * through the document's own Save As to a location the host would have asked for.
 */
@OptIn(ExperimentalTestApi::class)
class SaveCopyTest {
    @Test
    fun `save a copy keeps both versions and settles the question`() =
        runSkikoComposeUiTest(size = SIZE) {
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = conflicted(store)
            setContent { DraftsApp(document = document, saveAs = { document.saveAs(COPY) }) }
            askToSave()

            onNodeWithContentDescription(words(Res.string.save_copy)).performClick()
            waitUntil(timeoutMillis = TIMEOUT) { !asking() }

            assertEquals(MINE, runBlocking { store.read(COPY) }.text, "The reader's version is not in the copy")
            assertEquals(THEIRS, runBlocking { store.read(REF) }.text, "The other version was touched")
            assertEquals(DocumentState.Clean, document.lifecycle.state)
        }

    @Test
    fun `a copy the reader did not finish leaves the question open`() =
        runSkikoComposeUiTest(size = SIZE) {
            // Cancelling the save dialog is not an answer to 8.2's question.
            val document = conflicted(FakeDocumentStore(REF, ORIGINAL))
            setContent { DraftsApp(document = document, saveAs = { null }) }
            askToSave()

            onNodeWithContentDescription(words(Res.string.save_copy)).performClick()
            waitForIdle()

            assertTrue(asking(), "Cancelling the copy answered the conflict")
        }

    @Test
    fun `without a save dialog there is no copy to offer`() =
        runSkikoComposeUiTest(size = SIZE) {
            setContent { DraftsApp(document = conflicted(FakeDocumentStore(REF, ORIGINAL))) }
            askToSave()

            assertTrue(onAllNodesWithContentDescription(words(Res.string.save_copy)).fetchSemanticsNodes().isEmpty())
        }

    /** Ctrl+S, which 8.2 refuses, and waits for the question. */
    private fun SkikoComposeUiTest.askToSave() {
        waitForIdle()
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
        waitUntil(timeoutMillis = TIMEOUT) { asking() }
    }

    private fun SkikoComposeUiTest.asking(): Boolean =
        onAllNodesWithContentDescription(words(Res.string.conflict)).fetchSemanticsNodes().isNotEmpty()

    /** A document with an edit of its own, whose file someone else has since changed. */
    private fun conflicted(store: FakeDocumentStore): OpenDocument {
        val document =
            runBlocking {
                val contents = store.read(REF)
                OpenDocument(
                    store,
                    EditorState(DocumentSession(contents.text)),
                    DocumentSessionState.opened(REF, contents),
                )
            }
        val block = document.editor.blocks.first()
        document.editor.place(Caret(block.id, 0))
        document.editor.replace(requireNotNull(block.block.source), MINE.trimEnd(), MINE.trimEnd().length)
        store.changeOnDisk(REF, THEIRS)
        return document
    }

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L
        val REF = DocumentRef("/documents/chapter.md")
        val COPY = DocumentRef("/documents/chapter (my version).md")
        const val ORIGINAL = "As opened.\n"
        const val MINE = "My edit.\n"
        const val THEIRS = "Someone else's edit.\n"
    }
}
