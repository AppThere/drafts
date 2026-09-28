package com.appthere.drafts.app

import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Save and *Save As*, and what the reader is told about each (8.2, 7.4).
 *
 * Three outcomes and a cancel, settled in one place: the words are in the file, 8.2 refused, or the
 * write failed. The failure used to be silent -- safe, because the snapshot kept the words, and
 * wrong, because the reader believed they were in a file.
 */
class SavingTest {
    @Test
    fun `saving an untitled document asks where`() =
        runTest {
            // 7.4: "The first save is *Save As*".
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = openUntitled(store, DRAFT)
            var asked = 0

            Saving(document, keeper = null).save {
                asked++
                null
            }

            assertEquals(1, asked)
        }

    @Test
    fun `a cancelled save as says nothing and changes nothing`() =
        runTest {
            val saving = Saving(openUntitled(FakeDocumentStore(REF, ORIGINAL), DRAFT), keeper = null)

            saving.saveAs { null }

            assertFalse(saving.failed)
            assertNull(saving.refusal)
        }

    @Test
    fun `a save that fails is said out loud until the reader has read it`() =
        runTest {
            val store = FakeDocumentStore(REF, ORIGINAL).apply { failsToWrite = true }
            val saving = Saving(opened(store), keeper = null)

            saving.save(saveAs = null)
            assertTrue(saving.failed, "A failed save said nothing")

            saving.acknowledged()
            assertFalse(saving.failed)
        }

    @Test
    fun `a save refused because the file changed opens the question`() =
        runTest {
            val store = FakeDocumentStore(REF, ORIGINAL)
            val saving = Saving(opened(store), keeper = null)
            store.changeOnDisk(REF, THEIRS)

            saving.save(saveAs = null)
            assertNotNull(saving.refusal)

            saving.answered()
            assertNull(saving.refusal)
        }

    @Test
    fun `a save that happened starts the snapshot's retention clock`() =
        runTest {
            // 8.3: retained "for 30 days after a successful save". Only after one: a refused or
            // failed save that stamped the clock would let the only copy of the work be pruned.
            val store = FakeDocumentStore(REF, ORIGINAL)
            val document = opened(store)
            val snapshots = SnapshotStore(store, "/snapshots")
            SessionList(snapshots).opened(IDENTITY)
            val saving = Saving(document, SnapshotKeeper(document, snapshots, IDENTITY))

            saving.save(saveAs = null)

            assertNotNull(snapshots.recordOf(IDENTITY.documentId)?.savedAt)
        }

    @Test
    fun `a successful save as clears an earlier failure`() =
        runTest {
            val store = FakeDocumentStore(REF, ORIGINAL).apply { failsToWrite = true }
            val document = openUntitled(store, DRAFT)
            val saving = Saving(document, keeper = null)
            val target = DocumentRef("/documents/draft.md")

            saving.saveAs { document.saveAs(target) }
            assertTrue(saving.failed)

            store.failsToWrite = false
            saving.saveAs { document.saveAs(target) }
            assertFalse(saving.failed)
        }

    private suspend fun opened(store: FakeDocumentStore): OpenDocument {
        val contents = store.read(REF)
        return OpenDocument(
            store,
            EditorState(DocumentSession(contents.text)),
            DocumentSessionState.opened(REF, contents),
        )
    }

    private companion object {
        val REF = DocumentRef("/documents/note.md")
        const val ORIGINAL = "As opened.\n"
        const val THEIRS = "Someone else's edit.\n"
        const val DRAFT = "A first draft.\n"
        val IDENTITY =
            SessionIdentity(
                documentId = "note",
                uri = "file:///documents/note.md",
                displayName = "note.md",
                kind = "markdown",
            )
    }
}
