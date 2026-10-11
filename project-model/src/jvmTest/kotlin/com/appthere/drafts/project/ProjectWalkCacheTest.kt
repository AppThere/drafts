package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderEntry
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.files.PathFolderListing
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `projects/folder-as-project`: the binder fills in as folders are read, and a walk on focus lists
 * only the folders that changed -- on a real filesystem, counting the listings.
 */
class ProjectWalkCacheTest {
    private val directory: Path = createTempDirectory("drafts-walk")
    private val root = DocumentRef(directory.toString())
    private val listing = Counting(PathFolderListing())
    private val walk = ProjectWalk(listing)

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `the top level is shown before deeper folders are read`() =
        runTest {
            file("Act 1/Scenes/dock.fountain")
            file("Notes/premise.md")

            val steps = walk.walking(root, "Salt").toList()

            val first = steps.first().project.root
            assertEquals(listOf("Act 1", "Notes"), first.children.map { it.name })
            assertTrue(first.children.all { (it as ProjectNode.Folder).loading })
            assertFalse(steps.first().complete)
            assertTrue(steps.last().complete)
            assertTrue(
                steps
                    .last()
                    .project.root
                    .folders()
                    .none { it.loading },
            )
        }

    @Test
    fun `opening lists every folder whatever the cache says`() =
        runTest {
            file("Act 1/open.md")
            file("Act 2/close.md")
            val cache = walk.walking(root, "Salt").last().cache
            listing.listed.clear()

            walk.walking(root, "Salt", cache, full = true).last()

            assertEquals(3, listing.listed.size)
        }

    @Test
    fun `a walk on focus lists only the folder that changed`() =
        runTest {
            file("Act 1/open.md")
            file("Act 2/Scenes/dock.fountain")
            val cache = walk.walking(root, "Salt").last().cache
            listing.listed.clear()

            file("Act 2/Scenes/arrival.fountain")
            touch("Act 2/Scenes", LATER)
            val after = walk.walking(root, "Salt", cache, full = false).last()

            assertEquals(listOf("/Act 2/Scenes"), listing.listed.map { it.removePrefix(directory.toString()) })
            assertEquals(walk.open(root, "Salt"), after.project)
        }

    @Test
    fun `a folder whose platform gives no time is always listed`() =
        runTest {
            file("Act 1/open.md")
            val untimed = Counting(PathFolderListing(), times = false)
            val cache = ProjectWalk(untimed).walking(root, "Salt").last().cache
            untimed.listed.clear()

            ProjectWalk(untimed).walking(root, "Salt", cache, full = false).last()

            assertEquals(2, untimed.listed.size)
        }

    @Test
    fun `a nested project is shown and not walked`() =
        runTest {
            file("Other/.drafts/project.toml")
            file("Other/Deep/scene.md")

            walk.walking(root, "Salt").last()

            assertEquals(listOf("", "/Other"), listing.listed.map { it.removePrefix(directory.toString()) })
        }

    private fun file(path: String) {
        val file = directory.resolve(path)
        file.parent.createDirectories()
        file.writeText(path)
        // Every folder at a known time, so that only what a test touches has changed.
        generateSequence(file.parent) { it.parent }.takeWhile { it.startsWith(directory) }.forEach {
            Files.setLastModifiedTime(it, FileTime.fromMillis(EARLIER))
        }
    }

    private fun touch(
        folder: String,
        millis: Long,
    ) {
        Files.setLastModifiedTime(directory.resolve(folder), FileTime.fromMillis(millis))
    }

    /** A listing that records which folders it was asked to list. */
    private class Counting(
        private val real: FolderListing,
        private val times: Boolean = true,
    ) : FolderListing {
        val listed = mutableListOf<String>()

        override suspend fun entriesOf(folder: DocumentRef): List<FolderEntry>? =
            real.entriesOf(folder).also {
                listed +=
                    folder.token
            }

        override suspend fun modifiedOf(folder: DocumentRef): Long = if (times) real.modifiedOf(folder) else 0

        override suspend fun parentOf(folder: DocumentRef): FolderEntry? = real.parentOf(folder)
    }

    private companion object {
        const val EARLIER = 1_780_000_000_000L
        const val LATER = 1_781_000_000_000L
    }
}
