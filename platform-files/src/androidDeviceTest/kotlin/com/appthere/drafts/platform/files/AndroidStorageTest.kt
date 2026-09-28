package com.appthere.drafts.platform.files

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Android half of the storage stack, on a real device.
 *
 * A host test cannot check any of this. `Context.filesDir` is a stub, the filesystem is the
 * developer's rather than Android's, and whether a rename over an existing file works there says
 * nothing about whether it works on the device -- which is the guarantee 8.1 rests on.
 *
 * The reader's own documents arrive through the Storage Access Framework and are covered by
 * `SafDocumentStore`; that needs a document provider and a granted URI, which is a different test.
 * What this covers is app-private storage, where 8.1's snapshots live and where the application
 * owns the filesystem outright.
 */
class AndroidStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = PathDocumentStore()
    private val sessions by lazy { androidSessionRoot(context) }
    private val snapshots by lazy { SnapshotStore(store, sessions) }

    @AfterTest
    fun clean() {
        File(androidDataRoot(context)).deleteRecursively()
    }

    @Test
    fun appPrivateStorageIsInsideTheApplicationsOwnFiles() {
        // 8.1 says "app-private storage", which on Android means a directory no other application
        // can read and the system removes on uninstall. External storage is neither.
        assertTrue(
            androidDataRoot(context).startsWith(context.filesDir.path),
            "Data root is ${androidDataRoot(context)}, outside ${context.filesDir}",
        )
    }

    @Test
    fun aSnapshotSurvivesBeingWrittenAndReadBack() =
        runTest {
            assertTrue(snapshots.write(record(), TEXT))

            assertEquals(TEXT, snapshots.textOf(ID))
            assertEquals(CARET, snapshots.recordOf(ID)?.caret)
        }

    @Test
    fun theAtomicWriteLeavesNoTemporaryFileOnAndroidsFilesystem() =
        runTest {
            // The rename either worked or it did not. A leftover temporary file is the shape of a
            // filesystem that refused the rename and fell back to something else.
            snapshots.write(record(), TEXT)
            snapshots.write(record(), "$TEXT again\n")

            val left = File(sessions, ID).list().orEmpty().toList()
            assertEquals(listOf("meta.json", "snapshot.md"), left.sorted())
        }

    @Test
    fun theDigestCheckRefusesAFileThatChangedUnderneath() =
        runTest {
            // 8.2 on a device. The digest is computed by Android's own MessageDigest provider, which is
            // a different implementation from the desktop JVM's -- and a save that compared digests
            // from two different implementations would refuse every write.
            val document = File(context.filesDir, "chapter.md")
            document.writeText(ORIGINAL)
            val ref = DocumentRef(document.path)
            val opened = store.read(ref)

            document.writeText("Someone else's edit.\n")
            val outcome = store.writeIfUnchanged(ref, "Mine.\n", opened.facts.digest)

            assertIs<WriteOutcome.Conflict>(outcome)
            assertEquals("Someone else's edit.\n", document.readText())
        }

    @Test
    fun anUnchangedFileIsWrittenOnDevice() =
        runTest {
            // The other half: the check has to let real saves through on Android as well as refuse
            // wrong ones, or the application could never save at all.
            val document = File(context.filesDir, "mine.md")
            document.writeText(ORIGINAL)
            val ref = DocumentRef(document.path)
            val opened = store.read(ref)

            assertIs<WriteOutcome.Written>(store.writeIfUnchanged(ref, "Saved.\n", opened.facts.digest))

            assertEquals("Saved.\n", document.readText())
        }

    @Test
    fun theSafStoreDeclaresThatItCannotWriteAtomically() =
        runTest {
            // The declaration is load-bearing: `SnapshotStore` refuses to be built on a store that
            // cannot rename, which is what stops 8.1's snapshots being put through SAF by accident.
            val saf = SafDocumentStore(context.contentResolver)

            assertFalse(saf.writesAtomically)
            assertFalse(runCatching { SnapshotStore(saf, sessions) }.isSuccess)
        }

    private fun record() =
        SessionRecord(
            documentId = ID,
            uri = "file:///chapter.md",
            displayName = "chapter.md",
            kind = "markdown",
            caret = CARET,
            scrollOffset = 0,
            baseDigest = sha256(ORIGINAL.encodeToByteArray()).toString(),
            snapshotPath = snapshots.snapshotOf(ID).token,
        )

    private companion object {
        const val ID = "chapter"
        const val TEXT = "# Chapter\n\nUnsaved work.\n"
        const val ORIGINAL = "As opened.\n"
        val CARET = CaretRecord(blockIndex = 4, offset = 9)
    }
}
