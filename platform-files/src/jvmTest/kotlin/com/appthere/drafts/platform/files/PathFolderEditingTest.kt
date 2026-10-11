package com.appthere.drafts.platform.files

import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Renaming a file of the reader's own, which never replaces another. */
class PathFolderRenamingTest {
    private val directory: Path = createTempDirectory("drafts-rename")
    private val renaming = PathFolderRenaming()

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a file is renamed in its folder and keeps its words`() =
        runTest {
            directory.resolve("the-dock.fountain").writeText("INT. DOCK - NIGHT\n")

            val outcome =
                renaming.rename(
                    DocumentRef(directory.resolve("the-dock.fountain").toString()),
                    "02-the-dock.fountain",
                )

            assertEquals(
                RenameOutcome.Renamed(DocumentRef(directory.resolve("02-the-dock.fountain").toString())),
                outcome,
            )
            assertEquals("INT. DOCK - NIGHT\n", directory.resolve("02-the-dock.fountain").readText())
        }

    @Test
    fun `a name already taken is refused and nothing is replaced`() =
        runTest {
            directory.resolve("a.md").writeText("Mine.\n")
            directory.resolve("b.md").writeText("Theirs.\n")

            val outcome = renaming.rename(DocumentRef(directory.resolve("a.md").toString()), "b.md")

            assertEquals(RenameOutcome.Taken, outcome)
            assertEquals("Mine.\n", directory.resolve("a.md").readText())
            assertEquals("Theirs.\n", directory.resolve("b.md").readText())
        }

    @Test
    fun `a file that is not there is not renamed`() =
        runTest {
            val outcome = renaming.rename(DocumentRef(directory.resolve("gone.md").toString()), "02-gone.md")

            assertIs<RenameOutcome.Failed>(outcome)
            assertTrue(!directory.resolve("02-gone.md").exists())
        }
}
