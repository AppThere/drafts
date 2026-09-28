package com.appthere.drafts.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.CaretRecord
import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.Recovery
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.sha256
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 8.3 on launch, through the whole stack against real files.
 *
 * "On launch, for each restored session, compare snapshot digest against the file's current digest.
 * If they differ, the snapshot holds unsaved work. Open the document with the snapshot content and
 * an unobtrusive banner."
 *
 * The part worth testing at this level is that the *restoring* happens before anything is shown and
 * that the document is honest about it afterwards -- a restored document whose badge said `Saved`
 * would invite the reader to close it and lose the work a second time.
 */
@OptIn(ExperimentalTestApi::class)
class RecoveryTest {
    private val directory: Path = createTempDirectory("drafts-recovery")
    private val store = PathDocumentStore()
    private val snapshots = SnapshotStore(store, directory.resolve("sessions").toString())

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a session that ended without saving opens on the snapshot`() {
        // The whole point. The words exist nowhere but the snapshot, and the reader gets them back
        // without having to ask or even know there was a crash.
        givenSnapshot(UNSAVED)

        runSkikoComposeUiTest(size = SIZE) {
            open()

            onNodeWithText(UNSAVED_LINE).assertExists()
        }
    }

    @Test
    fun `the restored document says it has unsaved work`() {
        // 8.4's badge, on a document nobody has typed into yet. The text on screen is not the text
        // on disk, which is exactly what `dirty` means -- and a restored document reporting `Saved`
        // would invite the reader to close it and lose the same work twice.
        givenSnapshot(UNSAVED)

        runSkikoComposeUiTest(size = SIZE) {
            open()

            onNodeWithContentDescription("${Strings.DOCUMENT_STATE}, ${Strings.STATE_DIRTY}").assertExists()
        }
    }

    @Test
    fun `the banner says what happened`() {
        givenSnapshot(UNSAVED)

        runSkikoComposeUiTest(size = SIZE) {
            open()

            onNodeWithText(Strings.RESTORED, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `a session that ended with a save opens on the file and says nothing`() {
        // The ordinary case. A banner after every clean session would train the reader to dismiss
        // the one that matters.
        givenSnapshot(ORIGINAL)

        runSkikoComposeUiTest(size = SIZE) {
            open()

            onNodeWithText(Strings.RESTORED, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithContentDescription("${Strings.DOCUMENT_STATE}, ${Strings.STATE_CLEAN}").assertExists()
        }
    }

    @Test
    fun `a document with no snapshot opens normally`() {
        file().writeText(ORIGINAL)

        runSkikoComposeUiTest(size = SIZE) {
            open()

            onNodeWithText(ORIGINAL_LINE).assertExists()
            onNodeWithText(Strings.RESTORED, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `saving restored work writes it to the file`() {
        // Restoring is only half of it. The work has to be able to leave the snapshot, and the save
        // has to compare against the file as it is rather than against the snapshot it came from.
        givenSnapshot(UNSAVED)

        runSkikoComposeUiTest(size = SIZE) {
            val document = open()

            runBlocking { document.save() }
            waitForIdle()

            assertEquals(UNSAVED, file().readText())
        }
    }

    @Test
    fun `keeping the restored work dismisses the banner and changes nothing`() {
        givenSnapshot(UNSAVED)

        runSkikoComposeUiTest(size = SIZE) {
            open()
            onNodeWithContentDescription(Strings.KEEP).performClick()
            waitForIdle()

            onNodeWithText(Strings.RESTORED, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText(UNSAVED_LINE).assertExists()
            assertEquals(ORIGINAL, file().readText())
        }
    }

    @Test
    fun `discarding goes back to the file and takes the snapshot with it`() {
        // The only way a snapshot is thrown away deliberately -- 8.3's "Never auto-discard" leaves
        // exactly this one door open. Reloading without discarding would offer the same restore on
        // the next launch, which is asking the reader the same question until they answer it
        // differently.
        givenSnapshot(UNSAVED)

        runSkikoComposeUiTest(size = SIZE) {
            open()
            onNodeWithContentDescription(Strings.DISCARD).performClick()
            waitUntil(timeoutMillis = TIMEOUT) {
                onAllNodesWithContentDescription(Strings.DISCARD).fetchSemanticsNodes().isEmpty()
            }

            onNodeWithText(ORIGINAL_LINE).assertExists()
            assertFalse(snapshotFile().exists(), "The discarded snapshot is still there")
        }
    }

    @Test
    fun `a save that lands starts the retention clock`() {
        // 8.3 counts its thirty days from "a successful save". Until one happens the snapshot is
        // the only copy of the work and must never be prunable.
        givenSnapshot(UNSAVED)

        runSkikoComposeUiTest(size = SIZE) {
            open()

            save()
            waitUntil(timeoutMillis = TIMEOUT) { file().readText() == UNSAVED }
            waitUntil(timeoutMillis = TIMEOUT) { runBlocking { snapshots.recordOf(ID)?.savedAt } != null }

            assertNotNull(runBlocking { snapshots.recordOf(ID)?.savedAt })
        }
    }

    @Test
    fun `a save that was refused does not start the retention clock`() {
        // The stamp says the work is safely in a file. A refused save is the case where it is
        // emphatically not, and stamping here would make the snapshot prunable while it was still
        // the only copy -- thirty days later, silently.
        givenSnapshot(UNSAVED)

        runSkikoComposeUiTest(size = SIZE) {
            open()
            file().writeText("Someone else's edit.\n")

            save()
            waitUntil(timeoutMillis = TIMEOUT) {
                onAllNodesWithContentDescription(Strings.RELOAD).fetchSemanticsNodes().isNotEmpty()
            }

            assertNull(runBlocking { snapshots.recordOf(ID)?.savedAt })
        }
    }

    @Test
    fun `the caret comes back where the reader left it`() {
        // Phase 4 acceptance: "Session restores caret, scroll". 8.1 keeps meta.json beside the
        // snapshot for exactly this -- a manuscript restored at the top when the reader was in the
        // middle of a sentence has given back the words and lost the place.
        givenSnapshot(MANY_BLOCKS, caret = CaretRecord(blockIndex = 2, offset = 3))

        runSkikoComposeUiTest(size = SIZE) {
            val document = open()

            assertEquals(document.editor.blocks[2].id, document.editor.caret?.block)
            assertEquals(3, document.editor.caret?.offset)
        }
    }

    @Test
    fun `a caret beyond the end of the document is ignored rather than fatal`() {
        // A meta.json can outlive the text it describes -- written before an edit that removed
        // blocks, or simply damaged. Opening at the top is a small loss; failing to open is not.
        //
        // Ignored rather than clamped to the last block. Clamping would put the caret somewhere the
        // reader never was and reveal that block's raw source, which looks like the editor deciding
        // to open a different part of the document than the one they left.
        givenSnapshot(MANY_BLOCKS, caret = CaretRecord(blockIndex = 99, offset = 500))

        runSkikoComposeUiTest(size = SIZE) {
            val document = open()

            assertEquals(MANY_BLOCKS, document.editor.text)
            assertNull(document.editor.caret, "A caret nobody recorded was invented from a bad index")
        }
    }

    @Test
    fun `the scroll position comes back with the text`() {
        givenSnapshot(MANY_BLOCKS, scroll = SCROLL)

        runSkikoComposeUiTest(size = SIZE) {
            val document = open()

            assertEquals(SCROLL, document.restored?.scrollOffset)
        }
    }

    @Test
    fun `a document that was not restored has no caret to put back`() {
        // Opening a file normally should leave the caret nowhere, which is what puts every block in
        // preview state. A restored caret on an ordinary open would reveal a block nobody asked for.
        givenSnapshot(ORIGINAL)

        runSkikoComposeUiTest(size = SIZE) {
            val document = open()

            assertNull(document.editor.caret)
            assertNull(document.restored?.scrollOffset)
        }
    }

    private fun androidx.compose.ui.test.SkikoComposeUiTest.save() {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
    }

    /** Puts [text] in the snapshot and the original in the file, as a crashed session leaves them. */
    private fun givenSnapshot(
        text: String,
        caret: CaretRecord = CaretRecord(blockIndex = 0, offset = 0),
        scroll: Int = 0,
    ) {
        file().writeText(ORIGINAL)
        runBlocking { snapshots.write(record(caret, scroll), text) }
    }

    private fun androidx.compose.ui.test.SkikoComposeUiTest.open(): OpenDocument {
        val ref = DocumentRef(file().toString())
        val recover: suspend (Digest) -> Recovery = { digest -> snapshots.examine(ID, digest) }
        lateinit var document: OpenDocument

        setContent {
            val opening = rememberOpenDocument(store, ref, recover)
            if (opening is DocumentOpening.Opened) {
                document = opening.document
                DraftsApp(
                    document = opening.document,
                    keeper = SnapshotKeeper(opening.document, snapshots, IDENTITY),
                )
            }
        }
        waitUntil(timeoutMillis = TIMEOUT) {
            onAllNodesWithContentDescription(
                "${Strings.DOCUMENT_STATE}, ${Strings.STATE_DIRTY}",
            ).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithContentDescription(
                    "${Strings.DOCUMENT_STATE}, ${Strings.STATE_CLEAN}",
                ).fetchSemanticsNodes().isNotEmpty()
        }
        return document
    }

    private fun record(
        caret: CaretRecord = CaretRecord(blockIndex = 0, offset = 0),
        scroll: Int = 0,
    ) = SessionRecord(
        documentId = ID,
        uri = "file:///chapter.md",
        displayName = "chapter.md",
        kind = "markdown",
        caret = caret,
        scrollOffset = scroll,
        baseDigest = sha256(ORIGINAL.encodeToByteArray()).toString(),
        snapshotPath = snapshots.snapshotOf(ID).token,
    )

    private fun file(): Path = directory.resolve("chapter.md")

    private fun snapshotFile(): Path = directory.resolve("sessions/$ID/snapshot.md")

    private companion object {
        val SIZE = Size(1200f, 900f)
        const val TIMEOUT = 5_000L
        const val ID = "chapter"
        const val ORIGINAL = "As last saved.\n"
        const val ORIGINAL_LINE = "As last saved."
        const val UNSAVED = "Work that never reached the file.\n"
        const val UNSAVED_LINE = "Work that never reached the file."
        const val MANY_BLOCKS = "First.\n\nSecond.\n\nThird.\n\nFourth.\n"
        const val SCROLL = 8_123
        val IDENTITY =
            SessionIdentity(
                documentId = ID,
                uri = "file:///chapter.md",
                displayName = "chapter.md",
                kind = "markdown",
            )
    }
}
