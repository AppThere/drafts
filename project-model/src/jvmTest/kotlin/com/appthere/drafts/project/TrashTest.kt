package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.PathFolderEditing
import com.appthere.drafts.platform.files.PathFolderListing
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `projects/trash` on a real folder: deleting moves, restoring puts back, and only emptying removes. */
class TrashTest {
    private val directory: Path = createTempDirectory("drafts-trash")
    private val appData: Path = createTempDirectory("drafts-trash-app")
    private val root = DocumentRef(directory.toString())
    private val listing = PathFolderListing()
    private var now = DAY_ONE

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
        appData.toFile().deleteRecursively()
    }

    @Test
    fun `a deleted scene goes to the trash with its metadata, snapshots and place`() =
        runTest {
            scenes()
            val session = opened()

            assertEquals(Outcome.Done, Trash(session).delete(DOCK))
            session.reconcile()

            assertFalse(directory.resolve("Screenplay/02-dock.fountain").exists())
            val item = Trash(session).items().single()
            val held = Path.of(item.ref.token)
            assertEquals("THE DOCK", held.resolve("02-dock.fountain").readText())
            assertTrue(held.resolve(".meta.toml").exists())
            assertTrue(held.resolve(".snapshots/2026-10-01T1200.fountain").exists())
            assertEquals(
                TrashEntry("/Screenplay", "02-dock.fountain", "01-open.fountain", "03-close.fountain", false, DAY_ONE),
                item.entry,
            )
            assertTrue(held.startsWith(directory.resolve(".drafts/trash/Screenplay/02-dock.fountain")))
            val shown =
                session.view.value!!
                    .project.root
                    .folderAt(SCREENPLAY)!!
                    .children
            assertEquals(listOf("01-open.fountain", "03-close.fountain"), shown.map { it.name })
        }

    @Test
    fun `a restored scene goes back to its place with its snapshots`() =
        runTest {
            scenes()
            val session = opened()
            Trash(session).delete(DOCK)

            assertEquals(Outcome.Done, Trash(session).restore(Trash(session).items().single()))

            assertEquals(listOf("01-open.fountain", "02-dock.fountain", "03-close.fountain"), shown(session))
            assertTrue(directory.resolve(DOCK_SNAPSHOT).exists())
            assertTrue(directory.resolve(".drafts/meta/Screenplay/02-dock.fountain.toml").exists())
            assertTrue(Trash(session).items().isEmpty())
        }

    @Test
    fun `restoring over a name taken since replaces nothing and asks for another`() =
        runTest {
            scenes()
            val session = opened()
            Trash(session).delete(DOCK)
            file("Screenplay/02-dock.fountain", "A NEW DOCK")
            session.reconcile()
            val item = Trash(session).items().single()

            assertEquals(Outcome.Taken, Trash(session).restore(item))
            assertEquals("A NEW DOCK", directory.resolve("Screenplay/02-dock.fountain").readText())

            assertEquals(Outcome.Done, Trash(session).restore(item, "02-old-dock.fountain"))
            assertEquals("THE DOCK", directory.resolve("Screenplay/02-old-dock.fountain").readText())
            val snapshots = directory.resolve(".drafts/snapshots/Screenplay/02-old-dock.fountain")
            assertTrue(snapshots.resolve("2026-10-01T1200.fountain").exists())
        }

    @Test
    fun `restoring into a folder that has gone makes it again`() =
        runTest {
            file("Notes/premise.md", "PREMISE")
            val session = opened()
            Trash(session).delete(ProjectPath("/Notes/premise.md"))
            directory.resolve("Notes").toFile().deleteRecursively()
            session.reconcile()

            assertEquals(Outcome.Done, Trash(session).restore(Trash(session).items().single()))

            assertEquals("PREMISE", directory.resolve("Notes/premise.md").readText())
        }

    @Test
    fun `emptying one item removes it and nothing else`() =
        runTest {
            scenes()
            val session = opened()
            Trash(session).delete(DOCK)
            Trash(session).delete(SCREENPLAY.child("01-open.fountain"))
            val dock = Trash(session).items().single { it.entry.name == "02-dock.fountain" }

            assertEquals(Outcome.Done, Trash(session).empty(dock))

            assertEquals(listOf("01-open.fountain"), Trash(session).items().map { it.entry.name })
        }

    @Test
    fun `emptying the trash removes what is in it and nothing in the project`() =
        runTest {
            scenes()
            val session = opened()
            Trash(session).delete(DOCK)
            Trash(session).delete(SCREENPLAY.child("01-open.fountain"))

            assertEquals(Outcome.Done, Trash(session).emptyAll())

            assertTrue(Trash(session).items().isEmpty())
            assertEquals("CLOSE", directory.resolve("Screenplay/03-close.fountain").readText())
        }

    @Test
    fun `metadata left by a deletion outside the app is offered for removal only after 30 days`() =
        runTest {
            scenes()
            val session = opened()
            directory.resolve("Screenplay/02-dock.fountain").deleteExisting()
            session.reconcile()

            assertTrue(Trash(session).orphanedMetadata().isEmpty(), "Offered within 30 days")
            assertTrue(Trash(session).removeMetadata(DOCK) is Outcome.Failed)

            now = DAY_ONE + Reconciler.RETENTION_MILLIS + 1
            session.reconcile()
            assertEquals(listOf(DOCK), Trash(session).orphanedMetadata())

            assertEquals(Outcome.Done, Trash(session).removeMetadata(DOCK))
            assertFalse(directory.resolve(".drafts/meta/Screenplay/02-dock.fountain.toml").exists())
            assertTrue(directory.resolve(DOCK_SNAPSHOT).exists())
        }

    private suspend fun scenes() {
        ProjectManifest(listing, PathFolderEditing(), PathDocumentStore()).write(
            root,
            ProjectFile().reordered(SCREENPLAY, listOf("01-open.fountain", "02-dock.fountain", "03-close.fountain")),
            ManifestState.Absent,
        )
        file("Screenplay/01-open.fountain", "OPEN")
        file("Screenplay/02-dock.fountain", "THE DOCK")
        file("Screenplay/03-close.fountain", "CLOSE")
        file(".drafts/meta/Screenplay/02-dock.fountain.toml", "synopsis = \"Marla finds it\"")
        file(DOCK_SNAPSHOT, "EARLIER DOCK")
    }

    private suspend fun opened(): ProjectSession =
        ProjectSession(
            ProjectLocation(root, "Salt"),
            ProjectFiles(listing, PathFolderEditing(), PathDocumentStore()),
            ProjectStateStore(PathDocumentStore(), appData.toString()),
            now = { now },
        ).also { it.reconcile(full = true) }

    private fun shown(session: ProjectSession): List<String> =
        session.view.value!!
            .project.root
            .folderAt(SCREENPLAY)!!
            .children
            .map { it.name }

    private fun file(
        path: String,
        text: String,
    ) {
        val file = directory.resolve(path)
        file.parent.createDirectories()
        file.writeText(text)
    }

    private companion object {
        val SCREENPLAY = ProjectPath("/Screenplay")
        val DOCK = ProjectPath("/Screenplay/02-dock.fountain")
        const val DAY_ONE = 1_790_000_000_000L
        const val DOCK_SNAPSHOT = ".drafts/snapshots/Screenplay/02-dock.fountain/2026-10-01T1200.fountain"
    }
}
