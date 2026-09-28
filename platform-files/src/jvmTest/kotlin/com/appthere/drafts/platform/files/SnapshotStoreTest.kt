package com.appthere.drafts.platform.files

import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 8.1's snapshot on disk, through the real store.
 *
 * The directory is a temporary one standing in for app-private storage. What matters is that these
 * writes go somewhere that is not the user's file -- "Autosave never touches the user's file" is
 * the first sentence of 8.1 and the reason the whole mechanism is safe to run as often as it does.
 */
class SnapshotStoreTest {
    private val directory: Path = createTempDirectory("drafts-snapshots")
    private val store = PathDocumentStore()
    private val snapshots = SnapshotStore(store, directory.resolve("sessions").toString())

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a snapshot writes the text and the record`() =
        runTest {
            assertTrue(snapshots.write(record(), TEXT))

            assertEquals(TEXT, snapshots.textOf(ID))
            assertEquals(CARET, snapshots.recordOf(ID)?.caret)
        }

    @Test
    fun `the snapshot lands where 7 3 says it does`() =
        runTest {
            // The path goes into the session file as `snapshotPath`, so a restore on the next launch
            // can find it. A snapshot written somewhere else is a snapshot nobody will look for.
            snapshots.write(record(), TEXT)

            assertTrue(directory.resolve("sessions/$ID/snapshot.md").exists())
            assertTrue(directory.resolve("sessions/$ID/meta.json").exists())
        }

    @Test
    fun `the snapshot directory is created rather than required to exist`() =
        runTest {
            // First run of the application, or first time this document has been opened. Failing here
            // would mean autosave silently doing nothing until something else made the directory.
            assertFalse(directory.resolve("sessions").exists())

            assertTrue(snapshots.write(record(), TEXT))
        }

    @Test
    fun `snapshots do not touch the document they came from`() =
        runTest {
            // 8.1's first sentence, asserted. The reader's file is somewhere else entirely and the
            // snapshot has no way to reach it.
            val theirs = directory.resolve("chapter.md")
            theirs.writeText(ORIGINAL)

            snapshots.write(record(), TEXT)

            assertEquals(ORIGINAL, theirs.readText())
        }

    @Test
    fun `a later snapshot replaces the one before it`() =
        runTest {
            snapshots.write(record(), TEXT)

            snapshots.write(record(scroll = 9_000), "Later.\n")

            assertEquals("Later.\n", snapshots.textOf(ID))
            assertEquals(9_000, snapshots.recordOf(ID)?.scrollOffset)
        }

    @Test
    fun `two documents keep separate snapshots`() =
        runTest {
            // Sessions are per document, per 7.3's `sessions/uuid/`. One directory for all of them
            // would have the second document opened quietly overwrite the first one's unsaved work.
            snapshots.write(record(id = "one"), "First.\n")
            snapshots.write(record(id = "two"), "Second.\n")

            assertEquals("First.\n", snapshots.textOf("one"))
            assertEquals("Second.\n", snapshots.textOf("two"))
        }

    @Test
    fun `a document with no snapshot reads back as nothing`() =
        runTest {
            // The ordinary case on every launch: most documents were closed cleanly. It has to be a
            // null rather than an exception, or 8.3's recovery pass would fail on the first one.
            assertNull(snapshots.textOf("never-opened"))
            assertNull(snapshots.recordOf("never-opened"))
        }

    @Test
    fun `an unreadable record still leaves the text recoverable`() =
        runTest {
            // 8.3: "Never auto-discard a snapshot." The words do not stop being the reader's because
            // the caret position became unreadable, so a corrupt meta.json costs the scroll position
            // and nothing else.
            snapshots.write(record(), TEXT)
            directory.resolve("sessions/$ID/meta.json").writeText("{ not json at all")

            assertNull(snapshots.recordOf(ID))
            assertEquals(TEXT, snapshots.textOf(ID))
        }

    @Test
    fun `a record from a newer version still parses`() =
        runTest {
            // 7.3 will gain fields. An older build that threw on an unknown key would refuse to restore
            // a session written by a newer one -- and the file holds where the unsaved work is.
            snapshots.write(record(), TEXT)
            val meta = directory.resolve("sessions/$ID/meta.json")
            meta.writeText(meta.readText().replaceFirst("{", "{\n  \"somethingAddedLater\": 42,"))

            assertEquals(CARET, snapshots.recordOf(ID)?.caret)
        }

    @Test
    fun `discarding removes both files`() =
        runTest {
            // 8.3 prunes snapshots thirty days after a successful save. A meta.json left behind would
            // describe a snapshot that is not there.
            snapshots.write(record(), TEXT)

            assertTrue(snapshots.discard(ID))

            assertFalse(directory.resolve("sessions/$ID/snapshot.md").exists())
            assertFalse(directory.resolve("sessions/$ID/meta.json").exists())
        }

    @Test
    fun `a snapshot interrupted between its two files keeps the text`() =
        runTest {
            // The write order, which is otherwise invisible. If the process dies between the two
            // writes, the text with a stale meta.json restores the right words at a slightly wrong
            // caret; the other order keeps the caret and loses the words, which is not a trade anyone
            // would choose. Nothing else in this file can tell the two orders apart.
            val interrupted = SnapshotStore(StopsAfter(store, allowed = 1), directory.resolve("sessions").toString())

            assertFalse(interrupted.write(record(), TEXT))

            assertEquals(TEXT, snapshots.textOf(ID), "The words were the thing that did not get written")
            assertNull(snapshots.recordOf(ID))
        }

    private fun record(
        id: String = ID,
        scroll: Int = 8_123,
    ) = SessionRecord(
        documentId = id,
        uri = "file:///documents/chapter-3.md",
        displayName = "chapter-3.md",
        kind = "markdown",
        caret = CARET,
        scrollOffset = scroll,
        baseDigest = sha256(ORIGINAL.encodeToByteArray()).toString(),
        snapshotPath = snapshots.snapshotOf(id).token,
    )

    @Test
    fun `a snapshot holding the same words as the file is nothing to restore`() =
        runTest {
            // The ordinary case on every launch: the last session ended with a save, so the snapshot
            // and the file agree. Announcing restored changes here would cry wolf after every session
            // and teach the reader to dismiss the one banner that matters.
            snapshots.write(record(), ORIGINAL)

            assertIs<Recovery.NothingToRestore>(snapshots.examine(ID, sha256(ORIGINAL.encodeToByteArray())))
        }

    @Test
    fun `a snapshot holding more than the file is unsaved work`() =
        runTest {
            // 8.3: "If they differ, the snapshot holds unsaved work." The session ended without a save
            // -- a crash, a kill, a battery -- and these words exist nowhere else.
            snapshots.write(record(), TEXT)

            val recovery = snapshots.examine(ID, sha256(ORIGINAL.encodeToByteArray()))

            assertEquals(TEXT, (recovery as? Recovery.UnsavedWork)?.text)
        }

    @Test
    fun `a document with no snapshot has nothing to restore`() =
        runTest {
            // Walked for every restored session on launch, most of which have never been snapshotted.
            assertEquals(
                Recovery.NothingToRestore(record = null),
                snapshots.examine("never-opened", sha256(ORIGINAL.encodeToByteArray())),
            )
        }

    @Test
    fun `unsaved work is restored even when its record cannot be read`() =
        runTest {
            // "Never auto-discard a snapshot." An unreadable caret position is not a reason to discard
            // the sentence the caret was sitting in.
            snapshots.write(record(), TEXT)
            directory.resolve("sessions/$ID/meta.json").writeText("{ not json at all")

            val recovery = snapshots.examine(ID, sha256(ORIGINAL.encodeToByteArray()))

            assertEquals(TEXT, (recovery as? Recovery.UnsavedWork)?.text)
            assertNull((recovery as? Recovery.UnsavedWork)?.record)
        }

    @Test
    fun `the comparison is of digests and not of timestamps`() =
        runTest {
            // A snapshot is written on a timer, so it is almost always newer than the file even when it
            // says exactly the same thing. An mtime comparison would report unsaved work after every
            // session that ended normally.
            snapshots.write(record(), ORIGINAL)
            val snapshotFile = directory.resolve("sessions/$ID/snapshot.md")
            snapshotFile.setLastModifiedTime(
                java.nio.file.attribute.FileTime
                    .fromMillis(FAR_FUTURE),
            )

            assertIs<Recovery.NothingToRestore>(snapshots.examine(ID, sha256(ORIGINAL.encodeToByteArray())))
        }

    @Test
    fun `nothing to restore still says where the reader was`() =
        runTest {
            // 7.3 restores caret and scroll on every launch, not only after a crash. A clean session
            // has no words to give back and still has a place to give back.
            snapshots.write(record(), ORIGINAL)

            val recovery = snapshots.examine(ID, sha256(ORIGINAL.encodeToByteArray()))

            assertIs<Recovery.NothingToRestore>(recovery)
            assertEquals(CARET, recovery.record?.caret)
        }

    @Test
    fun `a snapshot does not erase the window's geometry`() =
        runTest {
            // The session list writes the window as it moves; the snapshot writes the text as it
            // changes. Each autosave used to write a record of its own with no window in it, so any
            // edit after the last move reopened the window at its default size.
            snapshots.write(record(), TEXT)
            snapshots.putRecord(requireNotNull(snapshots.recordOf(ID)).copy(window = WINDOW, closedAt = CLOSED))

            snapshots.write(record(), TEXT + "More.")

            assertEquals(WINDOW, snapshots.recordOf(ID)?.window)
            assertEquals(CLOSED, snapshots.recordOf(ID)?.closedAt)
        }

    @Test
    fun `a position can be remembered without touching the text`() =
        runTest {
            // For a document with nothing unsaved: its words are in the file already, so only the
            // place the reader got to is written.
            snapshots.write(record(), TEXT)
            val elsewhere = CaretRecord(blockIndex = 7, offset = 3)

            assertTrue(snapshots.rememberPosition(ID, elsewhere, scrollOffset = 99))

            assertEquals(elsewhere, snapshots.recordOf(ID)?.caret)
            assertEquals(99, snapshots.recordOf(ID)?.scrollOffset)
            assertEquals(TEXT, snapshots.textOf(ID), "Remembering a position rewrote the snapshot")
        }

    @Test
    fun `a session with no record has no position to remember`() =
        runTest {
            assertFalse(snapshots.rememberPosition("never-opened", CARET, scrollOffset = 0))
        }

    /** A store that stops writing partway, standing in for a process that stopped. */
    private class StopsAfter(
        private val delegate: DocumentStore,
        private val allowed: Int,
    ) : DocumentStore by delegate {
        private var writes = 0

        override suspend fun writeAtomically(
            ref: DocumentRef,
            text: String,
        ): WriteOutcome =
            if (writes++ >= allowed) {
                WriteOutcome.Unavailable(WriteOutcome.Reason.Failed, "stopped after $allowed")
            } else {
                delegate.writeAtomically(ref, text)
            }
    }

    private companion object {
        const val ID = "6f1c2f7e-0000-4000-8000-000000000001"
        const val CLOSED = 1_700_000_000_000L
        val WINDOW = WindowRecord(x = 120, y = 80, width = 900, height = 1100, placement = "floating")
        const val TEXT = "# Chapter 3\n\nUnsaved work.\n"
        const val ORIGINAL = "# Chapter 3\n"
        val CARET = CaretRecord(blockIndex = 42, offset = 17)

        /** Comfortably after any real snapshot, so a timestamp comparison would notice. */
        const val FAR_FUTURE = 4_000_000_000_000L
    }
}
