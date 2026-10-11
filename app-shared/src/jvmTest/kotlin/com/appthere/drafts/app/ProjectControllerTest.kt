package com.appthere.drafts.app

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.PathFolderEditing
import com.appthere.drafts.platform.files.PathFolderListing
import com.appthere.drafts.project.FolderOrder
import com.appthere.drafts.project.ManifestState
import com.appthere.drafts.project.Outcome
import com.appthere.drafts.project.ProjectFile
import com.appthere.drafts.project.ProjectFiles
import com.appthere.drafts.project.ProjectLocation
import com.appthere.drafts.project.ProjectManifest
import com.appthere.drafts.project.ProjectNode
import com.appthere.drafts.project.ProjectPath
import com.appthere.drafts.project.ProjectSession
import com.appthere.drafts.project.ProjectSettings
import com.appthere.drafts.project.ProjectStateStore
import com.appthere.drafts.project.nodeAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The binder's actions as the writer takes them, against a real folder, with a host that records. */
class ProjectControllerTest {
    private val directory: Path = createTempDirectory("drafts-controller")
    private val appData: Path = createTempDirectory("drafts-controller-app")
    private val root = DocumentRef(directory.toString())
    private val host = RecordingHost()

    @AfterTest
    fun cleanUp() {
        work.cancel()
        directory.toFile().deleteRecursively()
        appData.toFile().deleteRecursively()
    }

    @Test
    fun `move up from the keyboard reorders and says where it went`() =
        test {
            val controller = scenes()

            controller.step(ProjectPath("/02-dock.fountain"), -1)
            settled()

            assertEquals(listOf("02-dock.fountain", "01-open.fountain"), names(controller))
            assertEquals(Announcement.Moved(1, 2), controller.ui.value.announcement)
        }

    @Test
    fun `opening a new document opens it and clears its mark`() =
        test {
            val controller = scenes()
            file("pulled.md", "From a pull.")
            controller.reconcile()
            settled()
            assertEquals(
                setOf("/pulled.md"),
                controller.view.value!!
                    .pending.new,
            )

            controller.open(node(controller, "/pulled.md"))
            settled()

            assertEquals(listOf("/pulled.md"), host.opened)
            assertTrue(
                controller.view.value!!
                    .pending.new
                    .isEmpty(),
            )
        }

    @Test
    fun `a file that is not a document goes to the system`() =
        test {
            val controller = scenes()
            file("map.png", "PNG")
            controller.reconcile()
            settled()

            controller.open(node(controller, "/map.png"))
            settled()

            assertTrue(host.external.single().endsWith("map.png"))
            assertTrue(host.opened.isEmpty())
        }

    @Test
    fun `deleting a document with unsaved edits asks first and does nothing until answered`() =
        test {
            val controller = scenes()
            host.unsaved += "02-dock.fountain"

            controller.delete(node(controller, "/02-dock.fountain"))
            settled()

            assertIs<BinderPrompt.ConfirmDelete>(controller.ui.value.prompt)
            assertTrue(directory.resolve("02-dock.fountain").exists())

            controller.deleteNow(ProjectPath("/02-dock.fountain"))
            settled()
            assertFalse(directory.resolve("02-dock.fountain").exists())
        }

    @Test
    fun `a name already taken is said, with the name`() =
        test {
            val controller = scenes()

            controller.rename(ProjectPath("/02-dock.fountain"), "01-open.fountain")
            settled()

            assertEquals(Problem(Outcome.Taken, "01-open.fountain"), controller.ui.value.problem)
        }

    @Test
    fun `restoring onto a taken name asks for another`() =
        test {
            val controller = scenes()
            controller.deleteNow(ProjectPath("/02-dock.fountain"))
            settled()
            file("02-dock.fountain", "A new dock.")
            controller.reconcile()
            settled()

            controller.trashCan.restore(controller.trashCan.items().single())
            settled()

            assertIs<BinderPrompt.RestoreAs>(controller.ui.value.prompt)
        }

    @Test
    fun `a missing file opened asks where it is`() =
        test {
            val controller = scenes()
            Files.delete(directory.resolve("02-dock.fountain"))
            controller.reconcile()
            settled()

            controller.open(node(controller, "/02-dock.fountain"))
            settled()

            assertEquals(BinderPrompt.Locate(ProjectPath("/02-dock.fountain")), controller.ui.value.prompt)
        }

    /**
     * Where the controller runs what it starts; [settled] waits for all of it, since the folder's
     * work runs on real threads.
     */
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun test(body: suspend TestScope.() -> Unit) = runTest { body() }

    private suspend fun settled() {
        while (work.coroutineContext.job.children
                .any { it.isActive }
        ) {
            work.coroutineContext.job.children
                .forEach { it.join() }
        }
    }

    private suspend fun scenes(): ProjectController {
        file("01-open.fountain", "OPEN")
        file("02-dock.fountain", "THE DOCK")
        val listing = PathFolderListing()
        val store = PathDocumentStore()
        ProjectManifest(listing, PathFolderEditing(), store).write(
            root,
            ProjectFile(
                ProjectSettings(order = listOf(FolderOrder("/", listOf("01-open.fountain", "02-dock.fountain")))),
            ),
            ManifestState.Absent,
        )
        val session =
            ProjectSession(
                ProjectLocation(root, "Salt"),
                ProjectFiles(listing, PathFolderEditing(), store),
                ProjectStateStore(PathDocumentStore(), appData.toString()),
            )
        session.reconcile(full = true)
        return ProjectController(session, work, host)
    }

    private fun names(controller: ProjectController): List<String> =
        controller.view.value!!.project.root.children.map {
            it.name
        }

    private fun node(
        controller: ProjectController,
        path: String,
    ): ProjectNode =
        controller.view.value!!
            .project.root
            .nodeAt(ProjectPath(path))!!

    private fun file(
        path: String,
        text: String,
    ) {
        val file = directory.resolve(path)
        file.parent.createDirectories()
        file.writeText(text)
    }

    private class RecordingHost : ProjectHost {
        val opened = mutableListOf<String>()
        val external = mutableListOf<String>()
        val unsaved = mutableSetOf<String>()

        override fun openDocument(
            document: ProjectNode.Document,
            newWindow: Boolean,
        ) {
            opened += document.path.value
        }

        override fun openExternally(file: DocumentRef) {
            external += file.token
        }

        override fun hasUnsavedEdits(file: DocumentRef): Boolean = unsaved.any { file.token.endsWith(it) }
    }
}
