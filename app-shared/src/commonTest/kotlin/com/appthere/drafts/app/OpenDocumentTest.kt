package com.appthere.drafts.app

import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.editor.ui.replace
import com.appthere.drafts.platform.files.CaretRecord
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.DocumentState
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
            val store = FakeDocumentStore(REF, ORIGINAL)
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
            val store = FakeDocumentStore(REF, ORIGINAL)
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
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk(REF, "Theirs.\n")

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
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk(REF, "Theirs.\n")

            document.save()

            assertTrue(document.lifecycle.hasUnsavedEdits, "The edits were marked saved after a refusal")
        }

    @Test
    fun `a write that fails leaves the document dirty rather than clean`() =
        runTest {
            // A full disk. The file is untouched and the edits are not in it, so reporting clean here
            // would tell the reader their work was safe at the moment it definitely was not.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Edited.\n")
            store.failsToWrite = true

            assertIs<WriteOutcome.Unavailable>(document.save())

            assertEquals(DocumentState.Dirty, document.lifecycle.state)
        }

    @Test
    fun `a deleted file orphans the document on an attempted save`() =
        runTest {
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Edited.\n")
            store.remove(REF)

            document.save()

            assertEquals(DocumentState.Orphaned, document.lifecycle.state)
        }

    @Test
    fun `a read-only document reports read-only rather than clean`() =
        runTest {
            val document = opened(FakeDocumentStore(REF, ORIGINAL, writable = false))

            assertEquals(DocumentState.ReadOnly, document.lifecycle.state)
        }

    @Test
    fun `the save compares against the digest recorded at open`() =
        runTest {
            // Which digest is compared is the caller's choice, and the wrong choice is invisible in the
            // happy path. Comparing against a digest of the text in memory would match whatever the
            // reader had typed and overwrite the other version every time.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk(REF, "Theirs.\n")

            val conflict = assertIs<WriteOutcome.Conflict>(document.save())

            assertEquals(sha256(ORIGINAL.encodeToByteArray()), conflict.expected)
            assertEquals(sha256("Theirs.\n".encodeToByteArray()), conflict.found)
        }

    @Test
    fun `reloading takes the version from disk and clears the conflict`() =
        runTest {
            // 8.2's "[ Reload and lose my changes ]". Both halves have to happen: the text becomes what
            // is on disk, and the state stops being conflicted -- a document still marked conflicted
            // after reloading would refuse its own next save against a digest it has already replaced.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk(REF, THEIRS)
            document.save()

            document.reload()

            assertEquals(THEIRS, document.editor.text)
            assertEquals(DocumentState.Clean, document.lifecycle.state)
        }

    @Test
    fun `reloading discards the edits rather than keeping them undoable`() =
        runTest {
            // "lose my changes" has to mean it. A reader who reloads and then presses Ctrl+Z out of
            // habit must not get their discarded changes back on top of a document they were never
            // made against -- which is what reusing the editor and its history would do.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk(REF, THEIRS)
            document.save()

            document.reload()

            assertFalse(document.editor.undo(), "Undo after a reload brought the discarded changes back")
            assertEquals(THEIRS, document.editor.text)
        }

    @Test
    fun `saving after a reload writes against the version that was reloaded`() =
        runTest {
            // The point of clearing the conflict. The digest recorded at reload is the one the next
            // save compares against, so an ordinary save now goes through rather than being refused
            // against a digest nobody has held since before the reload.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.changeOnDisk(REF, THEIRS)
            document.save()
            document.reload()

            document.type("Later.\n")

            assertIs<WriteOutcome.Written>(document.save())
            assertEquals(DocumentState.Clean, document.lifecycle.state)
        }

    @Test
    fun `reloading a file that has gone leaves the document orphaned`() =
        runTest {
            // The reader asked to see what is on disk. Being told there is nothing there is an answer,
            // and a better one than an exception that takes the window with it.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            document.type("Mine.\n")
            store.remove(REF)

            document.reload()

            assertEquals(DocumentState.Orphaned, document.lifecycle.state)
        }

    /** Opens through the store the way [rememberOpenDocument] does, without a composition. */
    private suspend fun opened(store: FakeDocumentStore = FakeDocumentStore(REF, ORIGINAL)): OpenDocument {
        val contents = store.read(REF)
        return OpenDocument(
            store = store,
            editor = EditorState(DocumentSession(contents.text)),
            opened = DocumentSessionState.opened(REF, contents),
        )
    }

    @Test
    fun `an untitled document says so and has nothing for save to write to`() =
        runTest {
            // 7.4. Save returns no outcome, because nothing was attempted: the caller's answer is
            // Save As, and a silent write to some default location would be worse than either.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = openUntitled(store, DRAFT)
            document.type("More. ")

            assertEquals(DocumentState.Untitled, document.lifecycle.state)
            assertNull(document.save())
            assertEquals(0, store.writes)
        }

    @Test
    fun `save as puts the words in the file and the document becomes clean`() =
        runTest {
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = openUntitled(store, DRAFT)
            val target = DocumentRef("/documents/new.md")

            assertIs<WriteOutcome.Written>(document.saveAs(target))

            assertEquals(DRAFT, store.read(target).text)
            assertEquals(DocumentState.Clean, document.lifecycle.state)
            assertFalse(document.isUntitled)
        }

    @Test
    fun `after save as the next save is checked against what was written`() =
        runTest {
            // 8.2 applies from the first save on. Someone else editing the new file between saves
            // is exactly the conflict the digest check exists to catch.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = openUntitled(store, DRAFT)
            val target = DocumentRef("/documents/new.md")
            document.saveAs(target)
            store.changeOnDisk(target, THEIRS)
            document.type("Mine. ")

            assertIs<WriteOutcome.Conflict>(document.save())
            assertEquals(THEIRS, store.read(target).text)
        }

    @Test
    fun `an untitled document reopens with its words and caret`() {
        // Restored from its snapshot: the only copy of those words there is.
        val record = RECORD_AT_SECOND_BLOCK

        val document = openUntitled(FakeDocumentStore(REF, ORIGINAL), TWO_BLOCKS, record)

        assertEquals(TWO_BLOCKS, document.editor.text)
        assertEquals(1, document.editor.blocks.indexOfFirst { it.id == document.editor.caret?.block })
        assertEquals(record.scrollOffset, document.scrollOffset)
        assertFalse(document.restoredFromSnapshot, "An untitled document announced a restore it has no file for")
    }

    /**
     * Inserts [text] at the head of the first block, through the editor's own path so the revision
     * moves the way a keystroke moves it. The whole new block text is handed over because that is
     * what a field reports; `replace` narrows it back down to the run that actually changed.
     */
    private fun OpenDocument.type(text: String) {
        val block = editor.blocks.first()
        editor.place(Caret(block.id, 0))
        editor.replace(requireNotNull(block.block.source), text + editor.sourceOf(block.block), text.length)
    }

    private companion object {
        val REF = DocumentRef("/documents/note.md")
        const val DRAFT = "A first draft.\n"
        const val TWO_BLOCKS = "First.\n\nSecond.\n"
        val RECORD_AT_SECOND_BLOCK =
            SessionRecord(
                documentId = "untitled",
                uri = null,
                displayName = "Untitled",
                kind = "markdown",
                caret = CaretRecord(blockIndex = 1, offset = 2),
                scrollOffset = 640,
                baseDigest = null,
                snapshotPath = "/sessions/untitled/snapshot.md",
            )
        const val ORIGINAL = "As opened.\n"
        const val THEIRS = "Someone else's edit.\n"

        /** Two saves: the second has nothing new to write but must still be allowed to try. */
        const val EXPECTED_WRITES = 2
    }
}
