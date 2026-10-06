package com.appthere.drafts.platform.files

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Listing a folder of the reader's own, on a real filesystem. */
class PathFolderListingTest {
    private val directory: Path = createTempDirectory("drafts-listing")
    private val listing = PathFolderListing()

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a folder's files and folders are listed with what each one is`() =
        runTest {
            directory.resolve("premise.md").writeText("A premise.\n")
            directory.resolve("Notes").createDirectory()

            val entries = listing.entriesOf(DocumentRef(directory.toString())).orEmpty().sortedBy { it.name }

            assertEquals(listOf("Notes", "premise.md"), entries.map { it.name })
            assertEquals(listOf(true, false), entries.map { it.isFolder })
            assertEquals(directory.resolve("premise.md").toString(), entries[1].ref.token)
        }

    @Test
    fun `an empty folder is empty and a missing one could not be read`() =
        runTest {
            assertEquals(emptyList(), listing.entriesOf(DocumentRef(directory.toString())))
            assertNull(listing.entriesOf(DocumentRef(directory.resolve("gone").toString())))
        }

    @Test
    fun `a link to a folder is not walked into`() =
        runTest {
            // A link back up the tree would send a walk round in a circle.
            Files.createSymbolicLink(directory.resolve("loop"), directory)

            val entry = listing.entriesOf(DocumentRef(directory.toString())).orEmpty().single()

            assertEquals("loop", entry.name)
            assertTrue(!entry.isFolder, "A link was listed as a folder to walk")
        }
}
