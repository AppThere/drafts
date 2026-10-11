package com.appthere.drafts.platform.files

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The binder's changes to a folder of the reader's own, none of which ever replaces another. */
class PathFolderEditingTest {
    private val directory: Path = createTempDirectory("drafts-edit")
    private val editing = PathFolderEditing()

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a file is renamed in its folder and keeps its words`() =
        runTest {
            directory.resolve("the-dock.fountain").writeText("INT. DOCK - NIGHT\n")

            val outcome = editing.rename(ref("the-dock.fountain"), "02-the-dock.fountain")

            assertEquals(FolderOutcome.Done(ref("02-the-dock.fountain")), outcome)
            assertEquals("INT. DOCK - NIGHT\n", directory.resolve("02-the-dock.fountain").readText())
        }

    @Test
    fun `a name already taken is refused and nothing is replaced`() =
        runTest {
            directory.resolve("a.md").writeText("Mine.\n")
            directory.resolve("b.md").writeText("Theirs.\n")

            val outcome = editing.rename(ref("a.md"), "b.md")

            assertEquals(FolderOutcome.Taken, outcome)
            assertEquals("Mine.\n", directory.resolve("a.md").readText())
            assertEquals("Theirs.\n", directory.resolve("b.md").readText())
        }

    @Test
    fun `a file that is not there is not renamed`() =
        runTest {
            val outcome = editing.rename(ref("gone.md"), "02-gone.md")

            assertIs<FolderOutcome.Failed>(outcome)
            assertTrue(!directory.resolve("02-gone.md").exists())
        }

    @Test
    fun `a file moves into another folder with its words`() =
        runTest {
            directory.resolve("Act 2").createDirectories()
            directory.resolve("marla.md").writeText("Marla.\n")

            val outcome = editing.move(ref("marla.md"), ref(""), ref("Act 2"))

            assertEquals(FolderOutcome.Done(ref("Act 2/marla.md")), outcome)
            assertEquals("Marla.\n", directory.resolve("Act 2/marla.md").readText())
            assertFalse(directory.resolve("marla.md").exists())
        }

    @Test
    fun `a move onto a name already in the folder replaces nothing`() =
        runTest {
            directory.resolve("Characters").createDirectories()
            directory.resolve("marla.md").writeText("Mine.\n")
            directory.resolve("Characters/marla.md").writeText("Theirs.\n")

            val outcome = editing.move(ref("marla.md"), ref(""), ref("Characters"))

            assertEquals(FolderOutcome.Taken, outcome)
            assertEquals("Mine.\n", directory.resolve("marla.md").readText())
            assertEquals("Theirs.\n", directory.resolve("Characters/marla.md").readText())
        }

    @Test
    fun `a folder cannot be moved into itself`() =
        runTest {
            directory.resolve("Act 1/Scenes").createDirectories()

            val outcome = editing.move(ref("Act 1"), ref(""), ref("Act 1/Scenes"))

            assertIs<FolderOutcome.Failed>(outcome)
            assertTrue(directory.resolve("Act 1/Scenes").isDirectory())
        }

    @Test
    fun `a new file and a new folder are made, empty`() =
        runTest {
            assertEquals(FolderOutcome.Done(ref("scene.fountain")), editing.createFile(ref(""), "scene.fountain"))
            assertEquals(FolderOutcome.Done(ref("Notes")), editing.createFolder(ref(""), "Notes"))

            assertEquals("", directory.resolve("scene.fountain").readText())
            assertTrue(directory.resolve("Notes").isDirectory())
        }

    @Test
    fun `creating over an existing name replaces nothing`() =
        runTest {
            directory.resolve("scene.fountain").writeText("Words.\n")
            directory.resolve("Notes").createDirectories()
            directory.resolve("Notes/premise.md").writeText("Premise.\n")

            assertEquals(FolderOutcome.Taken, editing.createFile(ref(""), "scene.fountain"))
            assertEquals(FolderOutcome.Taken, editing.createFolder(ref(""), "Notes"))
            assertEquals(FolderOutcome.Taken, editing.createFile(ref(""), "Notes"))

            assertEquals("Words.\n", directory.resolve("scene.fountain").readText())
            assertEquals("Premise.\n", directory.resolve("Notes/premise.md").readText())
        }

    @Test
    fun `a name that would leave the folder is refused before anything is touched`() =
        runTest {
            directory.resolve("Notes").createDirectories()
            directory.resolve("a.md").writeText("A.\n")

            assertIs<FolderOutcome.Failed>(editing.createFile(ref("Notes"), "../escaped.md"))
            assertIs<FolderOutcome.Failed>(editing.createFolder(ref("Notes"), ".."))
            assertIs<FolderOutcome.Failed>(editing.rename(ref("a.md"), "Notes/a.md"))

            assertFalse(directory.resolve("escaped.md").exists())
            assertEquals("A.\n", directory.resolve("a.md").readText())
        }

    @Test
    fun `removing a folder removes what is in it and not what a link in it points at`() =
        runTest {
            val outside = createTempDirectory("drafts-outside")
            try {
                outside.resolve("keep.md").writeText("Keep.\n")
                directory.resolve("trash/old/deep").createDirectories()
                directory.resolve("trash/old/deep/scene.md").writeText("Gone.\n")
                Files.createSymbolicLink(directory.resolve("trash/old/link"), outside)

                val outcome = editing.remove(ref("trash/old"))

                assertEquals(FolderOutcome.Done(ref("trash/old")), outcome)
                assertFalse(directory.resolve("trash/old").exists())
                assertEquals("Keep.\n", outside.resolve("keep.md").readText())
            } finally {
                outside.toFile().deleteRecursively()
            }
        }

    @Test
    fun `removing what is not there fails`() =
        runTest {
            assertIs<FolderOutcome.Failed>(editing.remove(ref("gone")))
        }

    private fun ref(path: String): DocumentRef =
        DocumentRef(if (path.isEmpty()) directory.toString() else directory.resolve(path).toString())
}
