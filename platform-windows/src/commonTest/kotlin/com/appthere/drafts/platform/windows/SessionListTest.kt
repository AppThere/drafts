package com.appthere.drafts.platform.windows

import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentContents
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.FileFacts
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.WindowRecord
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 7.2's persisted session list.
 *
 * "Maintain a `List<DocumentSession>` in the application state and emit one `Window` per entry,
 * each with its own `WindowState` (position, size, placement) persisted." What is tested here is
 * the persisted half: which documents come back, and what they remember.
 *
 * The distinction the whole type turns on is *open* versus *has a snapshot*. Those are different
 * questions with different lifetimes -- 8.3 keeps a snapshot for thirty days after a save -- and
 * conflating them either reopens documents the reader shut last month or loses their work.
 */
class SessionListTest {
    @Test
    fun `an opened document is restorable`() =
        runTest {
            val sessions = sessions()

            sessions.opened(identity("one"))

            assertEquals(listOf("one"), sessions.restorable().map { it.documentId })
        }

    @Test
    fun `an untitled document is restorable too`() =
        runTest {
            // 7.4: an untitled document "has a session record whose uri is null, and is restored on
            // the next launch". Leaving it out would make closing the application lose its words.
            val sessions = sessions()
            val untitled = SessionIdentity.untitled(kind = "fountain", displayName = "Untitled")

            sessions.opened(untitled)

            val restored = sessions.restorable().single()
            assertEquals(untitled.documentId, restored.documentId)
            assertNull(restored.uri)
            assertNull(restored.baseDigest, "An untitled record claimed the digest of a file it does not have")
            assertEquals("fountain", restored.kind)
        }

    @Test
    fun `save as moves the record to the new file and keeps its id`() =
        runTest {
            // 7.4's first save. The next launch has to reopen the file, not an untitled document.
            val sessions = sessions()
            val untitled = SessionIdentity.untitled(kind = "markdown", displayName = "Untitled")
            sessions.opened(untitled)
            val moved =
                untitled.copy(
                    uri = "file:///documents/salt-road.fountain",
                    displayName = "salt-road.fountain",
                    kind = "fountain",
                    accessToken = "/documents/salt-road.fountain",
                )

            val updated = sessions.updated(moved)

            assertEquals(untitled.documentId, updated?.documentId)
            assertEquals(moved.uri, updated?.uri)
            assertEquals("fountain", updated?.kind)
            assertEquals(listOf("salt-road.fountain"), sessions.restorable().map { it.displayName })
        }

    @Test
    fun `save as with no session to move reports none`() =
        runTest {
            assertNull(sessions().updated(identity("nobody")))
        }

    @Test
    fun `a closed document is not restorable`() =
        runTest {
            val sessions = sessions()
            sessions.opened(identity("one"))

            sessions.closed("one", now = NOW)

            assertTrue(sessions.restorable().isEmpty())
        }

    @Test
    fun `closing keeps the record rather than deleting it`() =
        runTest {
            // 8.3's snapshot outlives the window. A document closed with unsaved work keeps its
            // snapshot indefinitely, and deleting the record here would orphan it -- the work would be
            // on disk with nothing left that knew where it came from.
            val store = store()
            val snapshots = SnapshotStore(store, ROOT)
            val sessions = SessionList(snapshots)
            sessions.opened(identity("one"))

            sessions.closed("one", now = NOW)

            assertEquals(NOW, snapshots.recordOf("one")?.closedAt)
        }

    @Test
    fun `reopening a closed document makes it restorable again`() =
        runTest {
            val sessions = sessions()
            sessions.opened(identity("one"))
            sessions.closed("one", now = NOW)

            sessions.opened(identity("one"))

            assertEquals(listOf("one"), sessions.restorable().map { it.documentId })
        }

    @Test
    fun `reopening keeps where the reader was`() =
        runTest {
            // The reason `opened` merges rather than replaces. A document reopened from the session
            // list should come back where it was left, and a fresh record would throw away the caret,
            // the scroll, the window and 8.3's retention stamp on the way in.
            val store = store()
            val snapshots = SnapshotStore(store, ROOT)
            val sessions = SessionList(snapshots)
            sessions.opened(identity("one"))
            sessions.remember("one", WINDOW)
            sessions.closed("one", now = NOW)

            sessions.opened(identity("one"))

            assertEquals(WINDOW, snapshots.recordOf("one")?.window)
        }

    @Test
    fun `a window remembers its geometry`() =
        runTest {
            val store = store()
            val snapshots = SnapshotStore(store, ROOT)
            val sessions = SessionList(snapshots)
            sessions.opened(identity("one"))

            assertTrue(sessions.remember("one", WINDOW))

            assertEquals(WINDOW, snapshots.recordOf("one")?.window)
        }

    @Test
    fun `a document nobody opened has no geometry to remember`() =
        runTest {
            assertFalse(sessions().remember("never-opened", WINDOW))
        }

    @Test
    fun `several documents all come back`() =
        runTest {
            // 7.2's whole point, and Phase 5's "Multiple documents side by side": the list is a list.
            val sessions = sessions()
            sessions.opened(identity("one", name = "a.md"))
            sessions.opened(identity("two", name = "b.md"))
            sessions.opened(identity("three", name = "c.md"))

            assertEquals(listOf("a.md", "b.md", "c.md"), sessions.restorable().map { it.displayName })
        }

    @Test
    fun `the order is stable rather than whatever the filesystem said`() =
        runTest {
            // Windows come back in the order this returns them. A filesystem listing order would give
            // the reader a different arrangement on every launch for no reason they could see.
            val sessions = sessions()
            sessions.opened(identity("z", name = "zebra.md"))
            sessions.opened(identity("a", name = "apple.md"))

            assertEquals(listOf("apple.md", "zebra.md"), sessions.restorable().map { it.displayName })
        }

    @Test
    fun `an unreadable record does not cost the other documents`() =
        runTest {
            // One damaged meta.json must not stop the launch. The reader loses that window and keeps
            // the rest, which is the difference between a bad session and a bad morning.
            val store = store()
            val sessions = SessionList(SnapshotStore(store, ROOT))
            sessions.opened(identity("good"))
            sessions.opened(identity("broken"))
            store.corrupt("$ROOT/broken/meta.json")

            assertEquals(listOf("good"), sessions.restorable().map { it.documentId })
        }

    @Test
    fun `a document that was never opened is not restorable`() =
        runTest {
            assertTrue(sessions().restorable().isEmpty())
            assertNull(SnapshotStore(store(), ROOT).recordOf("nothing"))
        }

    private fun sessions() = SessionList(SnapshotStore(store(), ROOT))

    private fun store() = RecordingStore()

    private fun identity(
        id: String,
        name: String = "$id.md",
    ) = SessionIdentity(
        documentId = id,
        uri = "file:///$name",
        displayName = name,
        kind = "markdown",
        accessToken = "/$name",
    )

    /** An in-memory store that writes atomically, which is all the session list needs of one. */
    private class RecordingStore : DocumentStore {
        override val writesAtomically: Boolean = true

        private val files = mutableMapOf<String, String>()

        fun corrupt(path: String) {
            files[path] = "{ not json at all"
        }

        override suspend fun read(ref: DocumentRef): DocumentContents {
            val text = checkNotNull(files[ref.token]) { "${ref.token} is not there" }

            return DocumentContents(text, FileFacts(sha256(text.encodeToByteArray()), text.length.toLong(), 1), true)
        }

        override suspend fun facts(ref: DocumentRef): FileFacts? = null

        override suspend fun writeIfUnchanged(
            ref: DocumentRef,
            text: String,
            expected: Digest,
        ): WriteOutcome = writeAtomically(ref, text)

        override suspend fun writeAtomically(
            ref: DocumentRef,
            text: String,
        ): WriteOutcome {
            files[ref.token] = text

            return WriteOutcome.Written(FileFacts(sha256(text.encodeToByteArray()), text.length.toLong(), 1))
        }

        override suspend fun children(ref: DocumentRef): List<DocumentRef> {
            val prefix = ref.token.trimEnd('/') + "/"

            return files.keys
                .filter { it.startsWith(prefix) }
                .map { DocumentRef(prefix + it.removePrefix(prefix).substringBefore('/')) }
                .distinct()
        }

        override suspend fun delete(ref: DocumentRef): Boolean = files.remove(ref.token) != null
    }

    private companion object {
        const val ROOT = "/sessions"
        const val NOW = 1_800_000_000_000L
        val WINDOW = WindowRecord(x = 120, y = 80, width = 900, height = 1100, placement = "floating")
    }
}
