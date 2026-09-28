package com.appthere.drafts.platform.files

import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 8.3's retention: "Retain snapshots for 30 days after a successful save, then prune."
 *
 * The sentence before it is the one that governs: "Never auto-discard a snapshot." The two are
 * reconciled by where the clock starts. A snapshot whose work reached the file thirty days ago is
 * a safety net nobody needs any more; a snapshot that was never followed by a save is the only
 * copy of something, and no amount of elapsed time changes that.
 *
 * Everything here is about the difference between those two, because getting it wrong in one
 * direction leaks disk forever and in the other deletes somebody's novel.
 */
class SnapshotPruningTest {
    private val directory: Path = createTempDirectory("drafts-pruning")
    private val store = PathDocumentStore()
    private val snapshots = SnapshotStore(store, directory.resolve("sessions").toString())

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a snapshot saved long ago is pruned`() =
        runTest {
            snapshots.write(record(ID), TEXT)
            snapshots.markSaved(ID, NOW - SnapshotStore.RETAIN_MILLIS)

            assertEquals(1, snapshots.prune(NOW))

            assertTrue(snapshots.textOf(ID) == null, "The expired snapshot is still there")
        }

    @Test
    fun `a snapshot saved recently is kept`() =
        runTest {
            // The safety net is the point of the thirty days. A save that turned out to be wrong is
            // recoverable for as long as the snapshot behind it survives.
            snapshots.write(record(ID), TEXT)
            snapshots.markSaved(ID, NOW - SnapshotStore.RETAIN_MILLIS + DAY)

            assertEquals(0, snapshots.prune(NOW))

            assertEquals(TEXT, snapshots.textOf(ID))
        }

    @Test
    fun `a snapshot that was never saved is never pruned`() =
        runTest {
            // "Never auto-discard a snapshot." This one holds work that reached no file at all --
            // a session that ended in a crash years ago is still the only copy of what it holds.
            snapshots.write(record(ID), TEXT)

            assertEquals(0, snapshots.prune(NOW + SnapshotStore.RETAIN_MILLIS * YEARS))

            assertEquals(TEXT, snapshots.textOf(ID))
        }

    @Test
    fun `a session whose record cannot be read is kept`() =
        runTest {
            // Unreadable is not the same as expired. The text beside the record is still the reader's,
            // and a corrupt meta.json is a reason to be careful rather than a licence to delete.
            snapshots.write(record(ID), TEXT)
            snapshots.markSaved(ID, NOW - SnapshotStore.RETAIN_MILLIS)
            directory.resolve("sessions/$ID/meta.json").toFile().writeText("{ not json")

            assertEquals(0, snapshots.prune(NOW))

            assertEquals(TEXT, snapshots.textOf(ID))
        }

    @Test
    fun `pruning one session leaves the others alone`() =
        runTest {
            snapshots.write(record("old"), TEXT)
            snapshots.markSaved("old", NOW - SnapshotStore.RETAIN_MILLIS)
            snapshots.write(record("fresh"), "Still wanted.\n")
            snapshots.markSaved("fresh", NOW)

            assertEquals(1, snapshots.prune(NOW))

            assertEquals("Still wanted.\n", snapshots.textOf("fresh"))
        }

    @Test
    fun `pruning with no sessions at all does nothing`() =
        runTest {
            // First run: the sessions directory does not exist yet. Launch must not depend on it.
            assertTrue(!directory.resolve("sessions").exists())

            assertEquals(0, snapshots.prune(NOW))
        }

    @Test
    fun `the session list is the directory itself`() =
        runTest {
            // Not a separate index file. An index is a second thing to keep in step with the first,
            // and a drifting index means a session nobody looks at holding work nobody knows about.
            snapshots.write(record("one"), TEXT)
            snapshots.write(record("two"), TEXT)

            assertEquals(listOf("one", "two"), snapshots.sessions().sorted())
        }

    @Test
    fun `a save stamps the record without disturbing the snapshot`() =
        runTest {
            // The stamp is metadata about the text, not a new version of it. Rewriting the snapshot
            // here would be a write with nothing to write, on the path that runs after every save.
            snapshots.write(record(ID), TEXT)

            assertTrue(snapshots.markSaved(ID, NOW))

            assertEquals(TEXT, snapshots.textOf(ID))
            assertEquals(NOW, snapshots.recordOf(ID)?.savedAt)
            assertEquals(CARET, snapshots.recordOf(ID)?.caret)
        }

    @Test
    fun `stamping a document with no snapshot does nothing`() =
        runTest {
            // Most saves. The document was never snapshotted, so there is no retention to start.
            assertTrue(!snapshots.markSaved("never-opened", NOW))
        }

    private fun record(id: String) =
        SessionRecord(
            documentId = id,
            uri = "file:///chapter.md",
            displayName = "chapter.md",
            kind = "markdown",
            caret = CARET,
            scrollOffset = 0,
            baseDigest = sha256(TEXT.encodeToByteArray()).toString(),
            snapshotPath = snapshots.snapshotOf(id).token,
        )

    private companion object {
        const val ID = "chapter"
        const val TEXT = "Work in progress.\n"
        const val NOW = 1_800_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
        const val YEARS = 100
        val CARET = CaretRecord(blockIndex = 3, offset = 11)
    }
}
