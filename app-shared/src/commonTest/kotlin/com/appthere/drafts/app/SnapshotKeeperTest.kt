package com.appthere.drafts.app

import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.editor.ui.replace
import com.appthere.drafts.platform.files.CaretRecord
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotSchedule
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.SnapshotTrigger
import com.appthere.drafts.platform.files.sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 8.1's autosave, joined to a document that is being edited.
 *
 * `SnapshotScheduleTest` proves the timing and `SnapshotStoreTest` proves the writing. What is left
 * is the join: that the text captured is the text in the editor, that the caret is recorded in a
 * form that survives a restart, and that the user's own file is never touched on the way past.
 */
class SnapshotKeeperTest {
    @Test
    fun `an idle pause after an edit captures the text`() =
        runTest {
            val keeper = keeper()
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            val trigger = keeper.subject.snapshotIfDue(START + SnapshotSchedule.IDLE_AFTER_MILLIS, SCROLL)

            assertEquals(SnapshotTrigger.Idle, trigger)
            assertEquals("Unsaved. $ORIGINAL", keeper.snapshots.textOf(ID))
        }

    @Test
    fun `the snapshot never touches the document it came from`() =
        runTest {
            // 8.1's first sentence. The store the document was opened through still holds the original
            // bytes: autosave has no path to them and no digest that would let it write them.
            val keeper = keeper()
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            keeper.subject.snapshotIfDue(START + SnapshotSchedule.IDLE_AFTER_MILLIS, SCROLL)

            assertEquals(ORIGINAL, keeper.files.read(REF).text)
        }

    @Test
    fun `the caret is recorded as an index rather than a block id`() =
        runTest {
            // Block ids are handed out per session and mean nothing after a restart. 7.3 stores
            // `blockIndex` for that reason, and a restored caret is most of the value of restoring a
            // long document at all.
            val keeper = keeper(TWO_BLOCKS)
            val second = keeper.document.editor.blocks[1]
            keeper.document.editor.place(Caret(second.id, OFFSET))
            keeper.subject.edited(START)

            keeper.subject.snapshotIfDue(START + SnapshotSchedule.IDLE_AFTER_MILLIS, SCROLL)

            val record = keeper.snapshots.recordOf(ID)
            assertEquals(1, record?.caret?.blockIndex)
            assertEquals(OFFSET, record?.caret?.offset)
        }

    @Test
    fun `the scroll position travels with the text`() =
        runTest {
            // 8.1: "so caret and scroll survive with the text". Restoring a manuscript at the top when
            // the reader was on page 200 is most of the way to not having restored it.
            val keeper = keeper()
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            keeper.subject.snapshotIfDue(START + SnapshotSchedule.IDLE_AFTER_MILLIS, SCROLL)

            assertEquals(SCROLL, keeper.snapshots.recordOf(ID)?.scrollOffset)
        }

    @Test
    fun `the digest of the file as opened is recorded for 8 3 to compare against`() =
        runTest {
            // 8.3 decides whether a snapshot holds unsaved work by comparing digests on launch. Without
            // this field there is nothing to compare and every snapshot would look like unsaved work.
            val keeper = keeper()
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            keeper.subject.snapshotIfDue(START + SnapshotSchedule.IDLE_AFTER_MILLIS, SCROLL)

            assertEquals(
                sha256(ORIGINAL.encodeToByteArray()).toString(),
                keeper.snapshots.recordOf(ID)?.baseDigest,
            )
        }

    @Test
    fun `losing focus with unsaved edits captures them`() =
        runTest {
            // 8.1's first trigger, and the one most likely to be the last moment anybody gets: the
            // application may be backgrounded and killed without another frame.
            val keeper = keeper()
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            assertTrue(keeper.subject.snapshotOn(SnapshotTrigger.FocusLost, SCROLL))
            assertEquals("Unsaved. $ORIGINAL", keeper.snapshots.textOf(ID))
        }

    @Test
    fun `losing focus with nothing unsaved writes nothing`() =
        runTest {
            // Alt-tabbing is not an edit. Rewriting an identical snapshot on every blur is I/O on a
            // path that runs constantly, and on mobile that is battery for no gain.
            val keeper = keeper()

            assertFalse(keeper.subject.snapshotOn(SnapshotTrigger.FocusLost, SCROLL))
            assertNull(keeper.snapshots.textOf(ID))
        }

    @Test
    fun `closing with nothing unsaved records where the reader is and leaves the text alone`() =
        runTest {
            // A document that was only read: no words to capture, but 7.3 still brings the reader
            // back to the caret and scroll they left. Captured once so a record exists, as the
            // session list's would.
            val keeper = keeper(TWO_BLOCKS)
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)
            keeper.subject.snapshotOn(SnapshotTrigger.FocusLost, SCROLL)
            val captured = keeper.snapshots.textOf(ID)

            val second = keeper.document.editor.blocks[1]
            keeper.document.editor.place(Caret(second.id, OFFSET))
            assertTrue(keeper.subject.snapshotOn(SnapshotTrigger.Closing, SCROLL + 1))

            assertEquals(
                1,
                keeper.snapshots
                    .recordOf(ID)
                    ?.caret
                    ?.blockIndex,
            )
            assertEquals(SCROLL + 1, keeper.snapshots.recordOf(ID)?.scrollOffset)
            assertEquals(captured, keeper.snapshots.textOf(ID), "Recording a position rewrote the snapshot")
        }

    @Test
    fun `closing records the scroll the window last reported`() =
        runTest {
            // The host fires the closing snapshot from outside the composition, with no scroll state
            // to hand. It used to pass zero, and every closed document reopened at the top.
            val keeper = keeper()
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)
            keeper.subject.scrolled(SCROLL)

            keeper.subject.snapshotOn(SnapshotTrigger.Closing)

            assertEquals(SCROLL, keeper.snapshots.recordOf(ID)?.scrollOffset)
        }

    @Test
    fun `an untitled document's snapshot records no file`() =
        runTest {
            val keeper = untitledKeeper(TWO_BLOCKS)
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            keeper.subject.snapshotOn(SnapshotTrigger.FocusLost, SCROLL)

            val record = requireNotNull(keeper.snapshots.recordOf(UNTITLED.documentId))
            assertNull(record.uri)
            assertNull(record.baseDigest)
            assertEquals("Unsaved. $TWO_BLOCKS", keeper.snapshots.textOf(UNTITLED.documentId))
        }

    @Test
    fun `closing an empty untitled document leaves nothing behind`() =
        runTest {
            // 7.4: "An untitled document that is still empty when closed is discarded". Its session
            // too, or the next launch would restore a blank window nobody asked for.
            val keeper = untitledKeeper("")
            keeper.snapshots.putRecord(untitledRecord())

            assertTrue(keeper.subject.snapshotOn(SnapshotTrigger.Closing))

            assertNull(keeper.snapshots.recordOf(UNTITLED.documentId))
            assertNull(keeper.snapshots.textOf(UNTITLED.documentId))
        }

    @Test
    fun `closing an untitled document with words in it keeps them`() =
        runTest {
            // The ordinary case, and the reason 7.4 needs no "Save changes?" prompt: the words stay
            // in the snapshot and come back on the next launch.
            val keeper = untitledKeeper(TWO_BLOCKS)
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            keeper.subject.snapshotOn(SnapshotTrigger.Closing)

            assertEquals("Unsaved. $TWO_BLOCKS", keeper.snapshots.textOf(UNTITLED.documentId))
        }

    @Test
    fun `an empty untitled document that only lost focus is kept`() =
        runTest {
            // Losing focus is not closing. A reader who switched away before typing has not given up
            // on the document.
            val keeper = untitledKeeper("")
            keeper.snapshots.putRecord(untitledRecord())

            keeper.subject.snapshotOn(SnapshotTrigger.FocusLost)

            assertEquals(untitledRecord().documentId, keeper.snapshots.recordOf(UNTITLED.documentId)?.documentId)
        }

    @Test
    fun `after save as the snapshots name the new file`() =
        runTest {
            // Otherwise the next autosave writes the old location -- none, for an untitled
            // document -- back over the record, and the next launch reopens it as untitled.
            val keeper = untitledKeeper(TWO_BLOCKS)
            val moved = UNTITLED.copy(uri = "file:///documents/draft.md", displayName = "draft.md")
            keeper.subject.movedTo(moved)
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            keeper.subject.snapshotOn(SnapshotTrigger.FocusLost)

            assertEquals(moved.uri, keeper.snapshots.recordOf(UNTITLED.documentId)?.uri)
        }

    @Test
    fun `a document cannot move to someone else's id`() =
        runTest {
            val keeper = untitledKeeper(TWO_BLOCKS)

            assertFailsWith<IllegalArgumentException> { keeper.subject.movedTo(IDENTITY) }
        }

    @Test
    fun `a captured document is not captured again until it changes`() =
        runTest {
            val keeper = keeper()
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)
            keeper.subject.snapshotOn(SnapshotTrigger.Closing, SCROLL)

            assertFalse(keeper.subject.hasUnsavedEdits())
            assertNull(keeper.subject.nextDueAt())
        }

    @Test
    fun `a snapshot that failed to write stays pending`() =
        runTest {
            // If a failed write cleared the schedule, the document would look captured while nothing
            // had been written and no later trigger would fire -- the work would be gone at the next
            // crash, with the mechanism that exists to prevent that reporting success.
            val keeper = keeper(store = FakeDocumentStore(REF, ORIGINAL).also { it.failsToWrite = true })
            keeper.document.type("Unsaved. ")
            keeper.subject.edited(START)

            assertNull(keeper.subject.snapshotIfDue(START + SnapshotSchedule.IDLE_AFTER_MILLIS, SCROLL))
            assertTrue(keeper.subject.hasUnsavedEdits(), "A failed snapshot reported the work captured")
        }

    private class Fixture(
        val subject: SnapshotKeeper,
        val document: OpenDocument,
        val snapshots: SnapshotStore,
        val files: FakeDocumentStore,
    )

    private suspend fun keeper(
        text: String = ORIGINAL,
        store: FakeDocumentStore = FakeDocumentStore(REF, text),
    ): Fixture {
        val contents = store.read(REF)
        val document =
            OpenDocument(
                store = store,
                editor = EditorState(DocumentSession(contents.text)),
                opened = DocumentSessionState.opened(REF, contents),
            )
        val snapshots = SnapshotStore(store, "/snapshots")
        return Fixture(
            subject = SnapshotKeeper(document, snapshots, IDENTITY),
            document = document,
            snapshots = snapshots,
            files = store,
        )
    }

    private fun untitledKeeper(text: String): Fixture {
        val store = FakeDocumentStore(REF, ORIGINAL)
        val document = openUntitled(store, text)
        val snapshots = SnapshotStore(store, "/snapshots")
        return Fixture(
            subject = SnapshotKeeper(document, snapshots, UNTITLED),
            document = document,
            snapshots = snapshots,
            files = store,
        )
    }

    private fun untitledRecord() =
        SessionRecord(
            documentId = UNTITLED.documentId,
            uri = null,
            displayName = UNTITLED.displayName,
            kind = UNTITLED.kind,
            caret = CaretRecord(blockIndex = -1, offset = 0),
            scrollOffset = 0,
            baseDigest = null,
            snapshotPath = "/snapshots/${UNTITLED.documentId}/snapshot.md",
        )

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
        val REF = DocumentRef("/documents/chapter-3.md")
        const val ID = "chapter-3"
        const val ORIGINAL = "As opened.\n"
        const val TWO_BLOCKS = "First paragraph.\n\nSecond paragraph.\n"
        const val START = 1_000_000L
        const val SCROLL = 8_123
        const val OFFSET = 17
        val UNTITLED =
            SessionIdentity(documentId = "untitled-1", uri = null, displayName = "Untitled", kind = "markdown")
        val IDENTITY =
            SessionIdentity(
                documentId = ID,
                uri = "file:///documents/chapter-3.md",
                displayName = "chapter-3.md",
                kind = "markdown",
            )
    }
}
