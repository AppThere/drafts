package com.appthere.drafts.app.android

import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.app.openUntitled
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What closing one document does, per `appthere-drafts.md` 7.3, 7.4 and 8.1.
 *
 * Android had none of this: a document backed out of or swiped away stayed in the session list, so
 * the next launch brought it back, and 8.1's "window close, before teardown" snapshot never fired.
 *
 * The lifecycle callback that calls this is the Activity's and is not tested here; what it calls is.
 */
class ClosingTest {
    private val directory: Path = createTempDirectory("drafts-closing")
    private val files = PathDocumentStore()
    private val snapshots = SnapshotStore(files, directory.resolve("sessions").toString())
    private val sessions = SessionList(snapshots)

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a closed document is not restored on the next launch`() =
        runBlocking {
            // 7.3 restores "every session still open". This one is not.
            val (identity, keeper) = open(DRAFT)

            closeDocument(sessions, keeper, identity.documentId)

            assertTrue(sessions.restorable().isEmpty(), "Closing left it open")
        }

    @Test
    fun `a closed document is remembered, not forgotten`() =
        runBlocking {
            // 8.3 keeps the snapshot after a close; only the reader's own Discard throws one away.
            // Without a record there would be nothing to reopen the document from either.
            val (identity, keeper) = open(DRAFT)

            closeDocument(sessions, keeper, identity.documentId)

            val record = assertNotNull(snapshots.recordOf(identity.documentId), "The record went with the window")

            assertTrue(record.closedAt != null, "The record was kept but not marked closed")
        }

    @Test
    fun `an untitled document that is still empty is discarded`() =
        runBlocking {
            // 7.4: "there is nothing in it to lose". Its session goes too, or the next launch would
            // restore a blank window nobody asked for.
            val (identity, keeper) = open("")

            closeDocument(sessions, keeper, identity.documentId)

            assertNull(snapshots.recordOf(identity.documentId), "An empty untitled document was kept")
            assertTrue(sessions.restorable().isEmpty())
        }

    @Test
    fun `the words of a document that has some are kept`() =
        runBlocking {
            // The other half of the same rule: emptiness is what makes a document disposable, not
            // being untitled. A first draft nobody saved is the work 8.1 exists for.
            val (identity, keeper) = open(DRAFT)

            closeDocument(sessions, keeper, identity.documentId)

            assertEquals(DRAFT, snapshots.textOf(identity.documentId))
        }

    @Test
    fun `closing one document leaves the others open`() =
        runBlocking {
            // On Android each document is its own task, so this runs whenever one of several goes.
            val (identity, keeper) = open(DRAFT)
            val other = sessions.opened(SessionIdentity.untitled(kind = "markdown", displayName = "Chapter two"))

            closeDocument(sessions, keeper, identity.documentId)

            assertEquals(listOf(other.documentId), sessions.restorable().map { it.documentId })
        }

    @Test
    fun `a document with no keeper is still closed`() =
        runBlocking {
            // A session whose document never finished opening has nothing to snapshot and is still
            // a window the reader shut.
            val identity = SessionIdentity.untitled(kind = "markdown", displayName = "Untitled")
            sessions.opened(identity)

            closeDocument(sessions, keeper = null, documentId = identity.documentId)

            assertTrue(sessions.restorable().isEmpty())
        }

    @Test
    fun `a closed document reopened from its Recents card is open again`() =
        runBlocking {
            // Android keeps a card for a task whose activity has finished, so a document closed
            // earlier can be reopened from one. Left marked closed it would be on screen and absent
            // from the next launch at the same time.
            val (identity, keeper) = open(DRAFT)
            closeDocument(sessions, keeper, identity.documentId)

            val record = assertNotNull(reopened(sessions, snapshots, identity.documentId))

            assertTrue(record.closedAt == null, "Reopening left it closed")
            assertEquals(listOf(identity.documentId), sessions.restorable().map { it.documentId })
        }

    @Test
    fun `a discarded document has no card to come back from`() =
        runBlocking {
            // 7.4 threw it away. There is nothing to reopen, and inventing a blank document would
            // be the one outcome "there is nothing in it to lose" rules out.
            val (identity, keeper) = open("")
            closeDocument(sessions, keeper, identity.documentId)

            assertNull(reopened(sessions, snapshots, identity.documentId))
        }

    /** An untitled document with [text] in it, recorded as open, with a keeper watching it. */
    private suspend fun open(text: String): Pair<SessionIdentity, SnapshotKeeper> {
        val identity = SessionIdentity.untitled(kind = "markdown", displayName = "Untitled")
        sessions.opened(identity)

        val document = openUntitled(files, text)
        val keeper = SnapshotKeeper(document, snapshots, identity)

        // The keeper is told there are unsaved edits, because that is the state a document is in
        // when the reader closes the window on it: words typed and never saved anywhere.
        keeper.edited(now = 1)

        return identity to keeper
    }

    private companion object {
        const val DRAFT = "# The Salt Road\n\nA first draft.\n"
    }
}
