package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.PathFolderListing
import com.appthere.drafts.platform.files.WriteOutcome
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `projects-core` design D3: this device's state for a project, kept apart from the project. */
class ProjectStateStoreTest {
    private val directory: Path = createTempDirectory("drafts-state-project")
    private val appData: Path = createTempDirectory("drafts-state-app")
    private val root = DocumentRef(directory.toString())

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
        appData.toFile().deleteRecursively()
    }

    @Test
    fun `a project with no sidecar keeps state and is not written to`() =
        runTest {
            directory.resolve("marla.md").writeText("Marla.")
            val states = ProjectStateStore(PathDocumentStore(), appData.toString())
            val pending = PendingState(new = setOf("/marla.md"))
            val index = ProjectIndex(files = listOf(IndexedFile("/marla.md", 1, "d")))

            assertTrue(states.write(root, pending, index))

            assertEquals(KeptState(index, pending), states.read(root))
            assertEquals(listOf("marla.md"), directory.listDirectoryEntries().map { it.fileName.toString() })
        }

    @Test
    fun `nothing kept reads as a baseline with nothing pending`() =
        runTest {
            assertEquals(
                KeptState(null, PendingState()),
                ProjectStateStore(PathDocumentStore(), appData.toString()).read(root),
            )
        }

    @Test
    fun `a crash between the two writes marks a file new again rather than losing the mark`() =
        runTest {
            val listing = PathFolderListing()
            val reconciler = Reconciler(PathDocumentStore())
            directory.resolve("marla.md").writeText("Marla.")
            val sound = ProjectStateStore(PathDocumentStore(), appData.toString())
            val first = reconciler.reconcile(open(listing), ProjectFile(), null, NOW)
            assertTrue(sound.write(root, PendingState().merged(first), first.index))

            directory.resolve("pulled.md").writeText("From a pull.")
            val second = reconciler.reconcile(open(listing), ProjectFile(), sound.read(root).index, NOW)
            val dying = ProjectStateStore(FailsOn("index.json", PathDocumentStore()), appData.toString())
            assertFalse(dying.write(root, sound.read(root).pending.merged(second), second.index))

            val kept = sound.read(root)
            val third = reconciler.reconcile(open(listing), ProjectFile(), kept.index, NOW)

            assertEquals(setOf("/pulled.md"), kept.pending.merged(third).new)
        }

    @Test
    fun `a store that cannot write atomically is refused`() {
        assertFailsWith<IllegalArgumentException> {
            ProjectStateStore(NotAtomic(PathDocumentStore()), appData.toString())
        }
    }

    private suspend fun open(listing: PathFolderListing): Project =
        requireNotNull(ProjectWalk(listing).open(root, "Salt"))

    /** A store whose process dies as it writes [name]: nothing is written. */
    private class FailsOn(
        private val name: String,
        private val real: DocumentStore,
    ) : DocumentStore by real {
        override suspend fun writeAtomically(
            ref: DocumentRef,
            text: String,
        ): WriteOutcome =
            if (ref.token.endsWith(name)) {
                WriteOutcome.Unavailable(WriteOutcome.Reason.Failed, "killed")
            } else {
                real.writeAtomically(ref, text)
            }
    }

    private class NotAtomic(
        private val real: DocumentStore,
    ) : DocumentStore by real {
        override val writesAtomically: Boolean = false
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
    }
}
