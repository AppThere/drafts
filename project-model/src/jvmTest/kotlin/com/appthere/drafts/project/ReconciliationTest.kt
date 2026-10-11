package com.appthere.drafts.project

import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentContents
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.FileFacts
import com.appthere.drafts.platform.files.FolderEntry
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.PathFolderListing
import com.appthere.drafts.platform.files.WriteOutcome
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `projects.md` 9: "the folder can be edited from outside the app and nothing breaks." On a real
 * filesystem, with the index kept between one reconcile and the next.
 */
class ReconciliationTest {
    private val directory: Path = createTempDirectory("drafts-reconcile")
    private val appData: Path = createTempDirectory("drafts-app-data")
    private val root = DocumentRef(directory.toString())
    private val listing = PathFolderListing()
    private val store = CountingStore(PathDocumentStore())
    private val states = ProjectStateStore(PathDocumentStore(), appData.toString())

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
        appData.toFile().deleteRecursively()
    }

    @Test
    fun `the first reconcile is a baseline and calls nothing new`() =
        runTest {
            file("Notes/premise.md", "A premise.")
            file("marla.md", "Marla.")

            val first = reconcile()

            assertTrue(first.added.isEmpty(), "Everything was new on first sight: ${first.added}")
            assertEquals(
                setOf("/Notes/premise.md", "/marla.md"),
                first.index.files
                    .map { it.path }
                    .toSet(),
            )
        }

    @Test
    fun `a file added from outside is new`() =
        runTest {
            file("marla.md", "Marla.")
            remember(reconcile())

            file("pulled-in.md", "From a git pull.")

            assertEquals(listOf(ProjectPath("/pulled-in.md")), reconcile().added)
        }

    @Test
    fun `a file deleted from outside is missing and remembered`() =
        runTest {
            file("marla.md", "Marla.")
            file("ezra.md", "Ezra.")
            remember(reconcile())

            directory.resolve("ezra.md").deleteExisting()
            val after = reconcile()

            assertEquals(listOf(ProjectPath("/ezra.md")), after.missing)
            assertEquals(listOf("/ezra.md"), after.index.missing.map { it.path })
        }

    @Test
    fun `a missing file is forgotten by the index after thirty days`() =
        runTest {
            file("ezra.md", "Ezra.")
            remember(reconcile(now = DAY_ONE))
            directory.resolve("ezra.md").deleteExisting()
            remember(reconcile(now = DAY_ONE))

            val later = reconcile(now = DAY_ONE + Reconciler.RETENTION_MILLIS + 1)

            assertTrue(later.index.missing.isEmpty())
            assertTrue(later.missing.isEmpty())
        }

    @Test
    fun `a name in the order is missing for as long as it is in the order`() =
        runTest {
            // 9: "Manifest references a path that no longer exists -> keep the entry, mark missing,
            // never silently drop it."
            file("marla.md", "Marla.")
            val order = ProjectFile().reordered(ProjectPath.Root, listOf("marla.md", "gone.md"))

            val after = reconcile(file = order, now = DAY_ONE + Reconciler.RETENTION_MILLIS * 2)

            assertEquals(listOf(ProjectPath("/gone.md")), after.missing)
        }

    @Test
    fun `a file renamed outside is offered back under its new name`() =
        runTest {
            // 9: "If a new file appears with matching content hash, offer to relink."
            file("Screenplay/the-dock.fountain", "INT. DOCK - NIGHT\n")
            remember(reconcile())

            directory.resolve("Screenplay/the-dock.fountain").moveTo(directory.resolve("Screenplay/02-dock.fountain"))
            val after = reconcile()

            assertEquals(
                listOf(
                    Relink(ProjectPath("/Screenplay/the-dock.fountain"), ProjectPath("/Screenplay/02-dock.fountain")),
                ),
                after.relinks,
            )
        }

    @Test
    fun `a file edited outside is modified and one only touched is not`() =
        runTest {
            file("edited.md", "Before.")
            file("touched.md", "Same.")
            remember(reconcile())

            file("edited.md", "After.", modified = LATER)
            Files.setLastModifiedTime(directory.resolve("touched.md"), FileTime.fromMillis(LATER))

            assertEquals(listOf(ProjectPath("/edited.md")), reconcile().modified)
        }

    @Test
    fun `an unchanged project is reconciled without reading a file`() =
        runTest {
            file("a.md", "A.")
            file("Notes/b.md", "B.")
            remember(reconcile())
            store.reads = 0

            reconcile()

            assertEquals(0, store.reads, "Reconciling an unchanged project read its files")
        }

    @Test
    fun `reconciling writes nothing in the reader's folder`() =
        runTest {
            // 9: "Reconciliation never writes to the user's files." And 13.1: a folder with no
            // sidecar is not given one by being reconciled -- its index is kept, but not there.
            file("marla.md", "Marla.")

            val reconciliation = reconcile()

            assertTrue(states.write(root, PendingState(), reconciliation.index))
            assertEquals(listOf("marla.md"), directory.listDirectoryEntries().map { it.fileName.toString() })
            assertEquals(0, store.writes)
        }

    @Test
    fun `the index is kept outside the project and read back`() =
        runTest {
            file("marla.md", "Marla.")

            val first = reconcile()

            assertTrue(states.write(root, PendingState(), first.index))
            assertEquals(first.index, states.read(root).index)
            assertFalse(directory.resolve(".drafts").toFile().exists())
        }

    @Test
    fun `a project in another place has an index of its own`() =
        runTest {
            file("marla.md", "Marla.")
            assertTrue(states.write(root, PendingState(), reconcile().index))

            assertEquals(null, states.read(DocumentRef(directory.resolve("elsewhere").toString())).index)
        }

    @Test
    fun `a file that comes back is neither new nor missing`() =
        runTest {
            // Switching git branches: gone on one, back on the other.
            file("marla.md", "Marla.")
            file("ezra.md", "Ezra.")
            remember(reconcile())
            val ezra = directory.resolve("ezra.md")
            ezra.deleteExisting()
            remember(reconcile())

            file("ezra.md", "Ezra.")
            val back = reconcile()

            assertEquals(listOf(ProjectPath("/ezra.md")), back.returned)
            assertTrue(back.added.isEmpty(), "A returning file was called new: ${back.added}")
            assertTrue(back.missing.isEmpty())
        }

    @Test
    fun `a file whose platform gives no modification time is read every time`() =
        runTest {
            file("marla.md", "Before.")
            remember(reconcile(listing = NoTimes(listing)))

            file("marla.md", "After.")
            val after = reconcile(listing = NoTimes(listing))

            assertEquals(listOf(ProjectPath("/marla.md")), after.modified)
        }

    @Test
    fun `two missing files with the same content are offered to nothing`() =
        runTest {
            file("a.md", "Same.")
            file("b.md", "Same.")
            remember(reconcile())

            directory.resolve("a.md").deleteExisting()
            directory.resolve("b.md").deleteExisting()
            file("c.md", "Same.")

            assertTrue(reconcile().relinks.isEmpty())
        }

    @Test
    fun `a missing file matching two new files is offered to neither`() =
        runTest {
            file("a.md", "Words.")
            remember(reconcile())

            directory.resolve("a.md").deleteExisting()
            file("b.md", "Words.")
            file("c.md", "Words.")

            assertTrue(reconcile().relinks.isEmpty())
        }

    @Test
    fun `blank files are never offered back`() =
        runTest {
            file("empty.md", "")
            file("spaces.md", "  \n\n ")
            remember(reconcile())

            directory.resolve("empty.md").deleteExisting()
            directory.resolve("spaces.md").deleteExisting()
            file("new-empty.md", "")
            file("new-spaces.md", "  \n\n ")

            assertTrue(reconcile().relinks.isEmpty())
        }

    @Test
    fun `a declined relink is not offered again under another name`() =
        runTest {
            file("the-dock.fountain", "INT. DOCK - NIGHT\n")
            remember(reconcile())
            directory.resolve("the-dock.fountain").moveTo(directory.resolve("02-dock.fountain"))
            val offered = reconcile()
            remember(offered)
            val declined =
                setOf(
                    Decline(
                        "/the-dock.fountain",
                        offered.index.files
                            .single()
                            .digest,
                    ),
                )

            directory.resolve("02-dock.fountain").moveTo(directory.resolve("03-dock.fountain"))

            val again = reconcile(declines = declined).relinks

            // 02-dock has gone missing in its turn and may be offered back; the declined file may not.
            assertTrue(again.none { it.from == ProjectPath("/the-dock.fountain") }, "Offered again: $again")
        }

    @Test
    fun `a missing file can be relinked to a file still marked new from before`() =
        runTest {
            // The new file arrived on one reconcile, and the old one went on the next.
            file("the-dock.fountain", "INT. DOCK - NIGHT\n")
            remember(reconcile())
            file("02-dock.fountain", "INT. DOCK - NIGHT\n")
            remember(reconcile())

            directory.resolve("the-dock.fountain").deleteExisting()
            val later = reconcile(unseen = setOf(ProjectPath("/02-dock.fountain")))

            assertEquals(
                listOf(Relink(ProjectPath("/the-dock.fountain"), ProjectPath("/02-dock.fountain"))),
                later.relinks,
            )
        }

    @Test
    fun `a folder renamed outside is one relink, nested folders and all`() =
        runTest {
            file("Act 1/open.md", "Open.")
            file("Act 1/Scenes/dock.fountain", "INT. DOCK - NIGHT\n")
            remember(reconcile())

            directory.resolve("Act 1").moveTo(directory.resolve("Act One"))
            val after = reconcile()

            assertEquals(listOf(Relink(ProjectPath("/Act 1"), ProjectPath("/Act One"))), after.folderRelinks)
            assertTrue(after.relinks.isEmpty(), "Files were offered one by one too: ${after.relinks}")
        }

    @Test
    fun `a renamed folder with one file edited is offered file by file`() =
        runTest {
            file("Act 1/open.md", "Open.")
            file("Act 1/dock.fountain", "INT. DOCK - NIGHT\n")
            remember(reconcile())

            directory.resolve("Act 1").moveTo(directory.resolve("Act One"))
            file("Act One/open.md", "Opened, and edited.", modified = LATER)
            val after = reconcile()

            assertTrue(after.folderRelinks.isEmpty())
            assertEquals(
                listOf(Relink(ProjectPath("/Act 1/dock.fountain"), ProjectPath("/Act One/dock.fountain"))),
                after.relinks,
            )
        }

    private var kept: ProjectIndex? = null

    private suspend fun reconcile(
        file: ProjectFile = ProjectFile(),
        now: Long = DAY_ONE,
        unseen: Set<ProjectPath> = emptySet(),
        declines: Set<Decline> = emptySet(),
        listing: FolderListing = this.listing,
    ): Reconciliation {
        val project = requireNotNull(ProjectWalk(listing).open(root, "Salt"))
        return Reconciler(store).reconcile(project, file, kept ?: states.read(root).index, now, unseen, declines)
    }

    /** A listing from a platform that reports no modification times, as some storage providers do. */
    private class NoTimes(
        private val real: FolderListing,
    ) : FolderListing {
        override suspend fun entriesOf(folder: DocumentRef): List<FolderEntry>? =
            real.entriesOf(folder)?.map { it.copy(modifiedEpochMillis = 0) }

        override suspend fun modifiedOf(folder: DocumentRef): Long = 0

        override suspend fun parentOf(folder: DocumentRef): FolderEntry? = real.parentOf(folder)
    }

    /** What the next reconcile compares against, as a project with a sidecar would keep it. */
    private fun remember(reconciliation: Reconciliation) {
        kept = reconciliation.index
    }

    private fun file(
        path: String,
        text: String,
        modified: Long = EARLIER,
    ) {
        val file = directory.resolve(path)
        file.parent.createDirectories()
        file.writeText(text)
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified))
    }

    /** The real store, counting what it reads and writes. */
    private class CountingStore(
        private val real: DocumentStore,
    ) : DocumentStore by real {
        var reads = 0
        var writes = 0

        override suspend fun facts(ref: DocumentRef): FileFacts? = real.facts(ref).also { reads++ }

        override suspend fun read(ref: DocumentRef): DocumentContents = real.read(ref).also { reads++ }

        override suspend fun writeAtomically(
            ref: DocumentRef,
            text: String,
        ): WriteOutcome = real.writeAtomically(ref, text).also { writes++ }

        override suspend fun writeIfUnchanged(
            ref: DocumentRef,
            text: String,
            expected: Digest,
        ): WriteOutcome = real.writeIfUnchanged(ref, text, expected).also { writes++ }
    }

    private companion object {
        const val DAY_ONE = 1_790_000_000_000L
        const val EARLIER = 1_780_000_000_000L
        const val LATER = 1_781_000_000_000L
    }
}
