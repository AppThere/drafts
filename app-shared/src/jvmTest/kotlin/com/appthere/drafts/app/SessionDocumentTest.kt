package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.platform.files.CaretRecord
import com.appthere.drafts.platform.files.DocumentState
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A session's document, opened whichever way it has to be (7.3, 7.4, 8.3), against real files.
 *
 * The case this was written for is 7.3's: "A document whose file has vanished opens read-only from
 * its snapshot with a clear banner offering *Save As*." It used to show the exception.
 */
@OptIn(ExperimentalTestApi::class)
class SessionDocumentTest {
    private val directory: Path = createTempDirectory("drafts-session-document")
    private val files = PathDocumentStore()
    private val snapshots = SnapshotStore(files, directory.resolve("sessions").toString())
    private val sessions = SessionList(snapshots)

    @AfterTest
    fun clean() {
        runCatching { Files.setPosixFilePermissions(file(), PosixFilePermissions.fromString("rw-r--r--")) }
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a file that is there opens from the file`() {
        file().writeText(ON_DISK)
        val record = session()

        val document = assertIs<DocumentOpening.Opened>(open(record)).document

        assertEquals(ON_DISK, document.editor.text)
        assertEquals(DocumentState.Clean, document.lifecycle.state)
    }

    @Test
    fun `a file that has vanished opens from its snapshot and says it is missing`() {
        file().writeText(ON_DISK)
        val record = session()
        runBlocking { snapshots.write(record.copy(caret = CaretRecord(blockIndex = 1, offset = 0)), KEPT) }
        file().deleteExisting()

        val document = assertIs<DocumentOpening.Opened>(open(record)).document

        assertEquals(KEPT, document.editor.text)
        assertEquals(DocumentState.Orphaned, document.lifecycle.state)
        assertTrue(document.needsSaveAs, "A document with no file offered to save into it")
        assertEquals(1, document.editor.blocks.indexOfFirst { it.id == document.editor.caret?.block })
    }

    @Test
    fun `a file that has vanished with nothing kept says so`() {
        file().writeText(ON_DISK)
        val record = session()
        file().deleteExisting()

        assertEquals(DocumentOpening.Failed(DocumentOpening.Reason.NothingKept), open(record))
    }

    @Test
    fun `a file that is there but cannot be read is not called missing`() {
        // Permissions, a lock, a failing disk. The file is not gone, and saying it was would send
        // the reader looking for something that is right where they left it.
        file().writeText(ON_DISK)
        val record = session()
        Files.setPosixFilePermissions(file(), PosixFilePermissions.fromString("---------"))

        assertEquals(DocumentOpening.Failed(DocumentOpening.Reason.Unreadable), open(record))
    }

    @Test
    fun `an untitled session opens from its snapshot`() {
        val identity = SessionIdentity.untitled(kind = "markdown", displayName = "Untitled")
        val record = runBlocking { sessions.opened(identity) }
        runBlocking { snapshots.write(record, KEPT) }

        val document = assertIs<DocumentOpening.Opened>(open(record)).document

        assertEquals(KEPT, document.editor.text)
        assertEquals(DocumentState.Untitled, document.lifecycle.state)
    }

    private fun open(record: SessionRecord): DocumentOpening {
        var opening: DocumentOpening = DocumentOpening.Opening
        runSkikoComposeUiTest(size = Size(800f, 600f)) {
            setContent { opening = rememberSessionDocument(files, snapshots, record) }
            waitUntil(timeoutMillis = TIMEOUT) { opening !is DocumentOpening.Opening }
        }
        return opening
    }

    private fun session(): SessionRecord =
        runBlocking { sessions.opened(desktopIdentity(file().toString(), "markdown")) }

    private fun file(): Path = directory.resolve("chapter.md")

    private companion object {
        const val TIMEOUT = 5_000L
        const val ON_DISK = "As on disk.\n"
        const val KEPT = "First, kept.\n\nSecond, kept.\n"
    }
}
