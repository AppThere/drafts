package com.appthere.drafts.project

import com.appthere.drafts.project.ProjectFixture.Companion.SCREENPLAY
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** `projects/binder` below the UI (task 5.3), and a project from a later version (task 5.7). */
class BinderOperationsTest {
    private val project = ProjectFixture()
    private val directory = project.directory
    private val root = project.root

    @AfterTest
    fun cleanUp() {
        project.cleanUp()
    }

    // 5.3 -------------------------------------------------------------------------------------

    @Test
    fun `a new scene is placed where it was made and marked neither new nor unsorted`() =
        runTest {
            order(ProjectFile().reordered(SCREENPLAY, listOf("one.fountain", "two.fountain", "four.fountain")))
            listOf("one", "two", "four").forEach { file("Screenplay/$it.fountain", it) }
            val session = opened()

            assertEquals(Outcome.Done, BinderOperations(session).createDocument(SCREENPLAY, "three.fountain", at = 2))
            session.reconcile()

            assertEquals(listOf("one.fountain", "two.fountain", "three.fountain", "four.fountain"), shown("Screenplay"))
            val made =
                session.view.value!!
                    .project.root
                    .nodeAt(SCREENPLAY.child("three.fountain"))!!
            assertFalse(made.unplaced)
            assertTrue(
                session.view.value!!
                    .pending.new
                    .isEmpty(),
            )
        }

    @Test
    fun `a name already taken is refused and nothing moves`() =
        runTest {
            file("marla.md", "Mine.")
            file("Characters/marla.md", "Theirs.")
            val session = opened()

            assertEquals(
                Outcome.Taken,
                BinderOperations(session).move(ProjectPath("/marla.md"), ProjectPath("/Characters")),
            )
            assertEquals(
                Outcome.Taken,
                BinderOperations(session).createDocument(ProjectPath("/Characters"), "marla.md", at = 0),
            )

            assertEquals("Mine.", directory.resolve("marla.md").readText())
            assertEquals("Theirs.", directory.resolve("Characters/marla.md").readText())
        }

    @Test
    fun `the first reorder makes the sidecar and a rename alone does not`() =
        runTest {
            file("a.md", "A.")
            file("b.md", "B.")
            val session = opened()

            assertEquals(Outcome.Done, BinderOperations(session).rename(ProjectPath("/a.md"), "c.md"))
            assertFalse(directory.resolve(".drafts").exists(), "A rename made a sidecar")

            assertEquals(Outcome.Done, BinderOperations(session).reorder(ProjectPath.Root, listOf("c.md", "b.md")))
            assertTrue(directory.resolve(".drafts/project.toml").readText().contains("\"c.md\", \"b.md\""))
        }

    @Test
    fun `a move arrives at the end of the destination's order`() =
        runTest {
            order(
                ProjectFile()
                    .reordered(ProjectPath.Root, listOf("Characters", "ezra.md"))
                    .reordered(ProjectPath("/Characters"), listOf("marla.md")),
            )
            file("Characters/marla.md", "Marla.")
            file("ezra.md", "Ezra.")
            val session = opened()

            assertEquals(
                Outcome.Done,
                BinderOperations(session).move(ProjectPath("/ezra.md"), ProjectPath("/Characters")),
            )

            assertEquals(listOf("marla.md", "ezra.md"), shown("Characters"))
            assertEquals(listOf("Characters"), shown(""))
        }

    @Test
    fun `remove from order is the way a missing name leaves`() =
        runTest {
            order(ProjectFile().reordered(ProjectPath.Root, listOf("marla.md", "gone.md")))
            file("marla.md", "Marla.")
            val session = opened()
            assertEquals(listOf("marla.md", "gone.md"), shown(""))

            assertEquals(Outcome.Done, BinderOperations(session).removeFromOrder(ProjectPath("/gone.md")))

            assertEquals(listOf("marla.md"), shown(""))
        }

    @Test
    fun `a prefix reorder refused part way renames nothing`() =
        runTest {
            order(ProjectFile(ProjectSettings(orderMode = OrderMode.Prefix)))
            listOf("a", "b", "c", "d", "e").forEach { file("Scenes/$it.md", it) }
            val session =
                opened(
                    ProjectFixture.FailingOn(
                        3,
                        com.appthere.drafts.platform.files
                            .PathFolderEditing(),
                    ),
                )

            val outcome =
                BinderOperations(
                    session,
                ).reorder(ProjectPath("/Scenes"), listOf("e.md", "d.md", "c.md", "b.md", "a.md"))

            assertTrue(outcome is Outcome.Failed, "$outcome")
            assertEquals(listOf("a.md", "b.md", "c.md", "d.md", "e.md"), names("Scenes"))
        }

    // 5.7 -------------------------------------------------------------------------------------

    @Test
    fun `a project saved by a later version is not written even when every key is known`() =
        runTest {
            file(".drafts/project.toml", "[project]\nschema = 2\norderMode = \"manifest\"\n")
            file("a.md", "A.")
            file("b.md", "B.")
            val before = directory.resolve(".drafts/project.toml").readText()
            val session = opened()

            val outcome = BinderOperations(session).reorder(ProjectPath.Root, listOf("b.md", "a.md"))

            assertEquals(Outcome.NotWritable, outcome)
            assertEquals(before, directory.resolve(".drafts/project.toml").readText())
            assertTrue((session.view.value!!.manifest as ManifestState.NotWritable).newer)
        }

    private suspend fun opened(
        editing: com.appthere.drafts.platform.files.FolderEditing =
            com.appthere.drafts.platform.files
                .PathFolderEditing(),
    ) = project.opened(editing)

    private suspend fun order(file: ProjectFile) = project.order(file)

    private fun file(
        path: String,
        text: String,
    ) = project.file(path, text)

    private fun names(folder: String) = project.names(folder)

    private suspend fun shown(folder: String) = project.shown(folder)

    private fun contents() = project.contents()
}
