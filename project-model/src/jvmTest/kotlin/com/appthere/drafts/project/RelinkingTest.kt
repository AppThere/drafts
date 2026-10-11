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

/** `projects/reconciliation`'s relinks, accepted, declined and made by hand (task 5.4). */
class RelinkingTest {
    private val project = ProjectFixture()
    private val directory = project.directory
    private val root = project.root

    @AfterTest
    fun cleanUp() {
        project.cleanUp()
    }

    // 5.4 -------------------------------------------------------------------------------------

    @Test
    fun `accepting a relink in one folder takes the missing file's place`() =
        runTest {
            order(ProjectFile().reordered(SCREENPLAY, listOf("open.fountain", "the-dock.fountain", "close.fountain")))
            listOf("open", "the-dock", "close").forEach { file("Screenplay/$it.fountain", it.uppercase()) }
            file(".drafts/snapshots/Screenplay/the-dock.fountain/2026-10-01T1200.fountain", "EARLIER DOCK")
            val session = opened()
            Files.move(directory.resolve("Screenplay/the-dock.fountain"), directory.resolve("Screenplay/dock.fountain"))
            session.reconcile()
            val offer =
                session.view.value!!
                    .pending.offers
                    .single()

            assertEquals(Outcome.Done, Relinking(session).accept(offer))

            assertEquals(listOf("open.fountain", "dock.fountain", "close.fountain"), shown("Screenplay"))
            assertTrue(
                directory.resolve(".drafts/snapshots/Screenplay/dock.fountain/2026-10-01T1200.fountain").exists(),
            )
            val after = session.view.value!!
            assertTrue(after.pending.offers.isEmpty() && after.pending.new.isEmpty())
            assertFalse(
                after.project.root
                    .nodeAt(SCREENPLAY.child("dock.fountain"))!!
                    .unplaced,
            )
        }

    @Test
    fun `accepting a relink across folders leaves the file where it is`() =
        runTest {
            order(ProjectFile().reordered(ProjectPath("/Act 1"), listOf("open.md", "dock.md")))
            file("Act 1/open.md", "OPEN")
            file("Act 1/dock.md", "DOCK")
            file("Act 2/.keep", "")
            val session = opened()
            Files.move(directory.resolve("Act 1/dock.md"), directory.resolve("Act 2/dock.md"))
            session.reconcile()

            assertEquals(
                Outcome.Done,
                Relinking(session).accept(
                    session.view.value!!
                        .pending.offers
                        .single(),
                ),
            )

            assertEquals(listOf("open.md"), shown("Act 1"))
            assertTrue(directory.resolve("Act 2/dock.md").exists())
        }

    @Test
    fun `declining keeps both and the offer does not come back`() =
        runTest {
            file("the-dock.fountain", "THE DOCK")
            val session = opened()
            Files.move(directory.resolve("the-dock.fountain"), directory.resolve("dock.fountain"))
            session.reconcile()

            Relinking(session).decline(
                session.view.value!!
                    .pending.offers
                    .single(),
            )
            session.reconcile()

            val view = session.view.value!!
            assertTrue(view.pending.offers.isEmpty())
            assertEquals(setOf("/dock.fountain"), view.pending.new)
            assertTrue(
                view.project.root.children
                    .any { it is ProjectNode.Missing && it.name == "the-dock.fountain" },
            )
        }

    @Test
    fun `locate relinks a renamed and edited file`() =
        runTest {
            order(ProjectFile().reordered(ProjectPath.Root, listOf("the-dock.fountain", "close.fountain")))
            file("the-dock.fountain", "THE DOCK")
            file("close.fountain", "CLOSE")
            val session = opened()
            Files.move(directory.resolve("the-dock.fountain"), directory.resolve("dock.fountain"))
            file("dock.fountain", "THE DOCK, REWRITTEN")
            session.reconcile()

            assertEquals(
                Outcome.Done,
                Relinking(session).locate(ProjectPath("/the-dock.fountain"), ProjectPath("/dock.fountain")),
            )

            assertEquals(listOf("dock.fountain", "close.fountain"), shown(""))
        }

    @Test
    fun `locate refuses a file with history of its own`() =
        runTest {
            order(ProjectFile().reordered(ProjectPath.Root, listOf("the-dock.fountain")))
            file("harbour.fountain", "HARBOUR")
            file(".drafts/snapshots/harbour.fountain/2026-10-01T1200.fountain", "EARLIER HARBOUR")
            val session = opened()

            val outcome = Relinking(session).locate(ProjectPath("/the-dock.fountain"), ProjectPath("/harbour.fountain"))

            assertEquals(Outcome.HasHistory, outcome)
            assertEquals(listOf("the-dock.fountain", "harbour.fountain"), shown(""))
        }

    @Test
    fun `a folder relink carries the folder's order`() =
        runTest {
            order(ProjectFile().reordered(ProjectPath("/Act 1"), listOf("c.md", "a.md", "b.md")))
            listOf("a", "b", "c").forEach { file("Act 1/$it.md", it.uppercase()) }
            val session = opened()
            Files.move(directory.resolve("Act 1"), directory.resolve("Act One"))
            session.reconcile()
            val offer =
                session.view.value!!
                    .pending.offers
                    .single()
            assertTrue(offer.folder)

            assertEquals(Outcome.Done, Relinking(session).accept(offer))

            assertEquals(listOf("c.md", "a.md", "b.md"), shown("Act One"))
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
