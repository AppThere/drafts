package com.appthere.drafts.app.desktop

import com.appthere.drafts.app.SaveAs
import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.app.openUntitled
import com.appthere.drafts.platform.files.DocumentState
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 7.4's *Save As* on the desktop, from the moment the reader has chosen a path, against real files.
 *
 * The dialog itself is the platform's and is not tested here; everything after it is.
 */
class SaveAsTest {
    private val directory: Path = createTempDirectory("drafts-save-as")
    private val files = PathDocumentStore()
    private val snapshots = SnapshotStore(files, directory.resolve("sessions").toString())
    private val sessions = SessionList(snapshots)

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `an untitled document's words land in the chosen file`() =
        runBlocking {
            val (document, record, keeper) = untitled()
            val target = directory.resolve("salt-road.md")

            val outcome = SaveAs(sessions) {}.to(destinationOf(target.toString(), record), document, record, keeper)

            assertIs<WriteOutcome.Written>(outcome)
            assertEquals(DRAFT, target.readText())
            assertEquals(DocumentState.Clean, document.lifecycle.state)
        }

    @Test
    fun `the session follows the document to its file and keeps its id`() =
        runBlocking {
            // The window's title is the record's name; the next launch reopens the record's file.
            val (document, record, keeper) = untitled()
            val target = directory.resolve("salt-road.md")
            var shown: SessionRecord? = null

            SaveAs(sessions) { shown = it }.to(destinationOf(target.toString(), record), document, record, keeper)

            assertEquals(record.documentId, shown?.documentId)
            assertEquals("salt-road.md", shown?.displayName)
            assertEquals(target.toString(), sessions.restorable().single().accessToken)
        }

    @Test
    fun `the extension chosen decides the kind`() =
        runBlocking {
            // 9.1: a reader who saves as .fountain has said it is a screenplay.
            val (document, record, keeper) = untitled()
            var shown: SessionRecord? = null

            val screenplay = destinationOf(directory.resolve("scene.fountain").toString(), record)

            SaveAs(sessions) { shown = it }.to(screenplay, document, record, keeper)

            assertEquals("fountain", shown?.kind)
        }

    @Test
    fun `a failed save as leaves the document untitled and its session where it was`() =
        runBlocking {
            // A "folder" that is a file, which nothing can write inside. The words stay in the
            // window and the snapshot; nothing points the session at a file that was never written.
            val (document, record, keeper) = untitled()
            val notAFolder = directory.resolve("notes.md").also { it.writeText("Already here.\n") }
            var shown: SessionRecord? = null

            val outcome =
                SaveAs(sessions) { shown = it }.to(
                    destinationOf(notAFolder.resolve("draft.md").toString(), record),
                    document,
                    record,
                    keeper,
                )

            assertIs<WriteOutcome.Unavailable>(outcome)
            assertEquals(DocumentState.Untitled, document.lifecycle.state)
            assertEquals(null, shown)
            assertEquals(null, sessions.restorable().single().uri)
        }

    private suspend fun untitled(): Triple<com.appthere.drafts.app.OpenDocument, SessionRecord, SnapshotKeeper> {
        val identity = SessionIdentity.untitled(kind = "markdown", displayName = "Untitled")
        val record = sessions.opened(identity)
        val document = openUntitled(files, DRAFT)
        return Triple(document, record, SnapshotKeeper(document, snapshots, identity))
    }

    private companion object {
        const val DRAFT = "# The Salt Road\n\nA first draft.\n"
    }
}
