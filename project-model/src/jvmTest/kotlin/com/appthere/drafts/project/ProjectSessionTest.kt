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

/**
 * `projects-core` tasks 5.1 and 5.2: one session per project, the app's own changes never reported
 * back, and a change that fails at any step leaving the project as it was.
 */
class ProjectSessionTest {
    private val project = ProjectFixture()
    private val directory = project.directory
    private val root = project.root

    @AfterTest
    fun cleanUp() {
        project.cleanUp()
    }

    // 5.1 -------------------------------------------------------------------------------------

    @Test
    fun `the app's own prefix reorder is not reported by the next reconcile`() =
        runTest {
            order(ProjectFile(ProjectSettings(orderMode = OrderMode.Prefix)))
            file("Screenplay/cold-open.fountain", "COLD OPEN")
            file("Screenplay/the-dock.fountain", "THE DOCK")
            val session = opened()

            assertEquals(
                Outcome.Done,
                BinderOperations(session).reorder(SCREENPLAY, listOf("the-dock.fountain", "cold-open.fountain")),
            )
            session.reconcile()

            val view = session.view.value!!
            assertEquals(listOf("01-the-dock.fountain", "02-cold-open.fountain"), names("Screenplay"))
            assertTrue(view.pending.new.isEmpty(), "Marked new: ${view.pending.new}")
            assertTrue(view.pending.offers.isEmpty(), "Offered: ${view.pending.offers}")
            assertTrue(
                view.project.root
                    .folderAt(SCREENPLAY)!!
                    .children
                    .none { it is ProjectNode.Missing },
            )
        }

    @Test
    fun `one project has one session in a process`() =
        runTest {
            val sessions = ProjectSessions { ProjectSession(it, project.files(), project.states()) }
            val location = ProjectLocation(root, "Salt")

            assertSame(sessions.sessionFor(location), sessions.sessionFor(location.copy(name = "Other name")))
        }

    // 5.2 -------------------------------------------------------------------------------------

    @Test
    fun `a rename that fails at any step leaves the project as it was`() =
        runTest {
            order(ProjectFile().reordered(SCREENPLAY, listOf("the-dock.fountain")))
            file("Screenplay/the-dock.fountain", "THE DOCK")
            file(".drafts/meta/Screenplay/the-dock.fountain.toml", "synopsis = \"Marla finds it\"")
            file(".drafts/snapshots/Screenplay/the-dock.fountain/2026-10-01T1200.fountain", "EARLIER DOCK")
            val before = contents()

            // The file, then its metadata, then its snapshots: refuse each in turn.
            for (refused in 1..3) {
                val session =
                    opened(
                        ProjectFixture.FailingOn(
                            refused,
                            com.appthere.drafts.platform.files
                                .PathFolderEditing(),
                        ),
                    )
                val outcome =
                    BinderOperations(
                        session,
                    ).rename(SCREENPLAY.child("the-dock.fountain"), "02-dock.fountain")

                assertTrue(outcome is Outcome.Failed, "Step $refused: $outcome")
                assertEquals(before, contents(), "Step $refused left the project changed")
            }
        }

    @Test
    fun `a project toml changed underneath stops a rename before any file moves`() =
        runTest {
            order(ProjectFile().reordered(SCREENPLAY, listOf("the-dock.fountain")))
            file("Screenplay/the-dock.fountain", "THE DOCK")
            val session = opened()
            directory
                .resolve(
                    ".drafts/project.toml",
                ).writeText(directory.resolve(".drafts/project.toml").readText() + "\n")

            val outcome = BinderOperations(session).rename(SCREENPLAY.child("the-dock.fountain"), "02-dock.fountain")

            assertEquals(Outcome.Conflict, outcome)
            assertTrue(directory.resolve("Screenplay/the-dock.fountain").exists())
        }

    @Test
    fun `a rename takes its metadata, snapshots and place with it`() =
        runTest {
            order(ProjectFile().reordered(SCREENPLAY, listOf("open.fountain", "the-dock.fountain", "close.fountain")))
            file("Screenplay/open.fountain", "OPEN")
            file("Screenplay/the-dock.fountain", "THE DOCK")
            file("Screenplay/close.fountain", "CLOSE")
            file(".drafts/meta/Screenplay/the-dock.fountain.toml", "synopsis = \"Marla finds it\"")
            file(".drafts/snapshots/Screenplay/the-dock.fountain/2026-10-01T1200.fountain", "EARLIER DOCK")
            val session = opened()

            assertEquals(
                Outcome.Done,
                BinderOperations(session).rename(SCREENPLAY.child("the-dock.fountain"), "dock.fountain"),
            )

            assertEquals("THE DOCK", directory.resolve("Screenplay/dock.fountain").readText())
            assertTrue(directory.resolve(".drafts/meta/Screenplay/dock.fountain.toml").exists())
            assertTrue(
                directory.resolve(".drafts/snapshots/Screenplay/dock.fountain/2026-10-01T1200.fountain").exists(),
            )
            assertEquals(listOf("open.fountain", "dock.fountain", "close.fountain"), shown("Screenplay"))
        }

    @Test
    fun `a renamed folder tells windows where each document in it went`() =
        runTest {
            file("Act 1/open.md", "OPEN")
            file("Act 1/Scenes/dock.fountain", "DOCK")
            val session = opened()
            val heard = mutableListOf<Moved>()
            val listening =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    session.moved.collect {
                        heard +=
                            it
                    }
                }

            assertEquals(Outcome.Done, BinderOperations(session).rename(ProjectPath("/Act 1"), "Act One"))
            listening.cancel()

            val dock = heard.single { it.from == ProjectPath("/Act 1/Scenes/dock.fountain") }
            assertEquals(ProjectPath("/Act One/Scenes/dock.fountain"), dock.to)
            assertEquals("DOCK", Path.of(dock.ref.token).readText())
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
