package com.appthere.drafts.platform.files

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Listing and changing a granted folder through the Storage Access Framework, against
 * [TestDocumentsProvider] (`projects-core` tasks 2.3 and 2.4).
 */
class SafFolderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val listing = SafFolderListing(context)
    private val editing = SafFolderEditing(context, listing)
    private val tree = DocumentRef(TestDocumentsProvider.tree.toString())
    private lateinit var top: File

    @BeforeTest
    fun setUp() {
        top = TestDocumentsProvider.reset(context)
    }

    @Test
    fun aFolderListsItsEntriesAndTheirFolders() =
        runTest {
            file("Notes/premise.md", "Premise.")
            file("marla.md", "Marla.")

            val entries = assertNotNull(listing.entriesOf(tree))
            assertEquals(setOf("Notes" to true, "marla.md" to false), entries.map { it.name to it.isFolder }.toSet())
            assertTrue(entries.all { it.modifiedEpochMillis > 0 })

            val notes = entries.single { it.name == "Notes" }
            assertEquals(listOf("premise.md"), assertNotNull(listing.entriesOf(notes.ref)).map { it.name })
        }

    @Test
    fun aProviderWithNoModifiedTimeListsZero() =
        runTest {
            file("marla.md", "Marla.")
            TestDocumentsProvider.reportsModified = false

            assertEquals(listOf(0L), assertNotNull(listing.entriesOf(tree)).map { it.modifiedEpochMillis })
        }

    @Test
    fun aFolderSaysWhenItLastChanged() =
        runTest {
            file("Notes/premise.md", "Premise.")
            File(top, "Notes").setLastModified(MODIFIED)
            val (notes) = refs("Notes")

            assertEquals(MODIFIED, listing.modifiedOf(notes))
            assertTrue(listing.modifiedOf(tree) > 0)

            TestDocumentsProvider.reportsModified = false
            assertEquals(0L, listing.modifiedOf(notes))
        }

    @Test
    fun aFolderThatIsGoneListsAsUnreadable() =
        runTest {
            file("Notes/premise.md", "Premise.")
            val notes = assertNotNull(listing.entriesOf(tree)).single().ref
            File(top, "Notes").deleteRecursively()

            assertEquals(null, listing.entriesOf(notes))
        }

    @Test
    fun aMoveTheProviderSupportsKeepsTheWords() =
        runTest {
            file("Act 2/.keep", "")
            file("marla.md", "Marla.")
            val (act, marla) = refs("Act 2", "marla.md")

            assertIs<FolderOutcome.Done>(editing.move(marla, tree, act))

            assertEquals("Marla.", File(top, "Act 2/marla.md").readText())
            assertFalse(File(top, "marla.md").exists())
        }

    @Test
    fun aFolderTheProviderCannotMoveIsCopiedVerifiedAndThenRemoved() =
        runTest {
            TestDocumentsProvider.supportsMove = false
            file("Act 1/Scenes/dock.fountain", "INT. DOCK - NIGHT")
            file("Act 1/open.md", "Open.")
            file("Draft/.keep", "")
            val (act, draft) = refs("Act 1", "Draft")

            assertIs<FolderOutcome.Done>(editing.move(act, tree, draft))

            assertEquals("INT. DOCK - NIGHT", File(top, "Draft/Act 1/Scenes/dock.fountain").readText())
            assertEquals("Open.", File(top, "Draft/Act 1/open.md").readText())
            assertFalse(File(top, "Act 1").exists())
        }

    @Test
    fun aMoveOntoATakenNameReplacesNothing() =
        runTest {
            TestDocumentsProvider.supportsMove = false
            file("Characters/marla.md", "Theirs.")
            file("marla.md", "Mine.")
            val (characters, marla) = refs("Characters", "marla.md")

            assertEquals(FolderOutcome.Taken, editing.move(marla, tree, characters))

            assertEquals("Mine.", File(top, "marla.md").readText())
            assertEquals("Theirs.", File(top, "Characters/marla.md").readText())
            assertEquals(listOf("marla.md"), File(top, "Characters").list()!!.toList())
        }

    @Test
    fun aRenameTheProviderChangesIsUndone() =
        runTest {
            TestDocumentsProvider.altersNames = true
            file("the-dock.fountain", "INT. DOCK - NIGHT")
            val (dock) = refs("the-dock.fountain")

            assertIs<FolderOutcome.Failed>(editing.rename(dock, "02: the dock.fountain"))

            assertEquals(listOf("the-dock.fountain"), top.list()!!.toList())
        }

    @Test
    fun aRenameKeepsTheWords() =
        runTest {
            file("the-dock.fountain", "INT. DOCK - NIGHT")
            val (dock) = refs("the-dock.fountain")

            assertIs<FolderOutcome.Done>(editing.rename(dock, "02-the-dock.fountain"))

            assertEquals("INT. DOCK - NIGHT", File(top, "02-the-dock.fountain").readText())
        }

    @Test
    fun creatingOverATakenNameReplacesNothingAndMakesNoCopy() =
        runTest {
            file("scene.md", "Words.")

            assertEquals(FolderOutcome.Taken, editing.createFile(tree, "scene.md"))

            assertEquals("Words.", File(top, "scene.md").readText())
            assertEquals(listOf("scene.md"), top.list()!!.toList())
        }

    @Test
    fun aNewFileAndFolderHaveExactlyTheirNames() =
        runTest {
            assertIs<FolderOutcome.Done>(editing.createFile(tree, "notes.md"))
            assertIs<FolderOutcome.Done>(editing.createFolder(tree, "Beats"))

            assertEquals(setOf("notes.md", "Beats"), top.list()!!.toSet())
            assertTrue(File(top, "Beats").isDirectory)
        }

    @Test
    fun removingAFolderRemovesWhatIsInIt() =
        runTest {
            file("trash/old/scene.md", "Gone.")
            val (trash) = refs("trash")

            assertIs<FolderOutcome.Done>(editing.remove(trash))

            assertFalse(File(top, "trash").exists())
        }

    private fun file(
        path: String,
        text: String,
    ) {
        val file = File(top, path)
        file.parentFile!!.mkdirs()
        file.writeText(text)
    }

    /** References for top-level names, as a listing of the tree gives them. */
    private suspend fun refs(vararg names: String): List<DocumentRef> {
        val entries = assertNotNull(listing.entriesOf(tree))
        return names.map { name -> entries.single { it.name == name }.ref }
    }

    private companion object {
        const val MODIFIED = 1_780_000_000_000L
    }
}
