package com.appthere.drafts.app

import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.DocumentState
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The seam between editing and the file: 8.4's state as the reader actually reaches it.
 *
 * [DocumentSessionStateTest] covers the transitions in isolation. What is left to check is the
 * wiring -- that typing reaches `dirty`, that a save reaches `clean` only when bytes were written,
 * and that the digest a save compares against is the one recorded at open.
 */
class OpenDocumentTest {
    @Test
    fun `a freshly opened document is clean`() =
        runTest {
            assertEquals(DocumentState.Clean, opened().lifecycle.state)
        }

    @Test
    fun `typing makes the document dirty`() =
        runTest {
            // The half of 8.4 that the store cannot see. Nothing has touched the file, so every fact
            // the store knows is unchanged -- the state has to come from the editor.
            val document = opened()

            document.type("Edited.\n")

            assertEquals(DocumentState.Dirty, document.lifecycle.state)
        }

    @Test
    fun `saving writes the text and returns the document to clean`() =
        runTest {
            val store = FakeDocumentStore(ORIGINAL)
            val document = opened(store)
            document.type("Edited.\n")

            assertIs<WriteOutcome.Written>(document.save())

            assertEquals(DocumentState.Clean, document.lifecycle.state)
            assertEquals(1, store.writes)
        }

    @Test
    fun `a second save with nothing new still succeeds`() =
        runTest {
            // Two things at once. `savedRevision` has to have been recorded by the first save, or the
            // document would stay dirty and the badge would never go back to clean. And an explicit
            // Ctrl+S is a request, not a suggestion -- it writes again rather than deciding for the
            // reader that it knows better. The digest check makes that safe; skipping it would not.
            val store = FakeDocumentStore(ORIGINAL)
            val document = opened(store)
            document.type("Edited.\n")
            document.save()

            assertIs<WriteOutcome.Written>(document.save())
            assertEquals(DocumentState.Clean, document.lifecycle.state)
            assertEquals(EXPECTED_WRITES, store.writes)
        }

    @Test
    fun `a file changed on disk is refused and the document becomes conflicted`() =
        runTest {
            // 8.2 end to end: the digest recorded at open no longer matches, so nothing is written.
            val store = FakeDocumentStore(ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk("Theirs.\n")

            val outcome = document.save()

            assertIs<WriteOutcome.Conflict>(outcome)
            assertEquals(DocumentState.Conflicted, document.lifecycle.state)
            assertEquals(0, store.writes)
        }

    @Test
    fun `a refused save leaves the edits unsaved`() =
        runTest {
            // The reason the refusal is safe. The work is still in memory and still marked as not on
            // disk, so 8.1's snapshot remains the thing standing between the reader and losing it.
            val store = FakeDocumentStore(ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk("Theirs.\n")

            document.save()

            assertTrue(document.lifecycle.hasUnsavedEdits, "The edits were marked saved after a refusal")
        }

    @Test
    fun `a write that fails leaves the document dirty rather than clean`() =
        runTest {
            // A full disk. The file is untouched and the edits are not in it, so reporting clean here
            // would tell the reader their work was safe at the moment it definitely was not.
            val store = FakeDocumentStore(ORIGINAL)
            val document = opened(store)
            document.type("Edited.\n")
            store.failsToWrite = true

            assertIs<WriteOutcome.Unavailable>(document.save())

            assertEquals(DocumentState.Dirty, document.lifecycle.state)
        }

    @Test
    fun `a deleted file orphans the document on an attempted save`() =
        runTest {
            val store = FakeDocumentStore(ORIGINAL)
            val document = opened(store)
            document.type("Edited.\n")
            store.remove()

            document.save()

            assertEquals(DocumentState.Orphaned, document.lifecycle.state)
        }

    @Test
    fun `a read-only document reports read-only rather than clean`() =
        runTest {
            val document = opened(FakeDocumentStore(ORIGINAL, writable = false))

            assertEquals(DocumentState.ReadOnly, document.lifecycle.state)
        }

    @Test
    fun `the save compares against the digest recorded at open`() =
        runTest {
            // Which digest is compared is the caller's choice, and the wrong choice is invisible in the
            // happy path. Comparing against a digest of the text in memory would match whatever the
            // reader had typed and overwrite the other version every time.
            val store = FakeDocumentStore(ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk("Theirs.\n")

            val conflict = assertIs<WriteOutcome.Conflict>(document.save())

            assertEquals(sha256(ORIGINAL.encodeToByteArray()), conflict.expected)
            assertEquals(sha256("Theirs.\n".encodeToByteArray()), conflict.found)
        }

    /** Opens through the store the way [rememberOpenDocument] does, without a composition. */
    private suspend fun opened(store: FakeDocumentStore = FakeDocumentStore(ORIGINAL)): OpenDocument {
        val contents = store.read(REF)
        return OpenDocument(
            store = store,
            editor = EditorState(DocumentSession(contents.text)),
            opened = DocumentSessionState.opened(REF, contents),
        )
    }

    /** An edit through the editor's own path, so the revision moves the way a keystroke moves it. */
    private fun OpenDocument.type(text: String) {
        val block = editor.blocks.first()
        editor.place(Caret(block.id, 0))
        editor.replace(requireNotNull(block.block.source), text, text.length)
    }

    private companion object {
        val REF = DocumentRef("/documents/note.md")
        const val ORIGINAL = "As opened.\n"

        /** Two saves: the second has nothing new to write but must still be allowed to try. */
        const val EXPECTED_WRITES = 2
    }
}
