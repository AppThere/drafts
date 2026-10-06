package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderEntry
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.intents.DocumentKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `projects.md` 14, step one: "Open a folder, build a tree, no metadata, no order." With the
 * settled questions of 13 that bear on it: a folder is a project without being made one, a nested
 * `.drafts/` is said rather than chosen, and non-text files are in the binder.
 */
class ProjectWalkTest {
    @Test
    fun `a folder of notes is a project as it stands`() =
        runTest {
            val project = open("Notes/premise.md", "Notes/timeline.md", "treatment.md")

            assertEquals("The Salt Road", project.name)
            assertFalse(project.hasSidecar, "A folder only opened was given a sidecar")
            assertEquals(
                listOf("/Notes/premise.md", "/Notes/timeline.md", "/treatment.md"),
                project.root.documents().map { it.path.value },
            )
        }

    @Test
    fun `each document has the kind its extension says`() =
        runTest {
            val project = open("marla.md", "02-the-dock.fountain", "scene.spmd")

            assertEquals(
                listOf(DocumentKind.Fountain, DocumentKind.Markdown, DocumentKind.Fountain),
                project.root.documents().map { it.kind },
            )
        }

    @Test
    fun `a file that is not text is in the binder and is not a document`() =
        runTest {
            // 13.3: "Non-text files are shown and opened externally."
            val project = open("Figures/map.png", "Sources/1890s-shipping.pdf", "notes.md")

            val attachments =
                project.root
                    .folders()
                    .flatMap { it.children }
                    .filterIsInstance<ProjectNode.Attachment>()
            assertEquals(listOf("map.png", "1890s-shipping.pdf"), attachments.map { it.name })
            assertEquals(listOf("notes.md"), project.root.documents().map { it.name })
        }

    @Test
    fun `things in a folder are alphabetical whatever their case`() =
        runTest {
            // No order yet (3's first two tiers come with project.toml), so the third: alphabetical.
            val project = open("beta.md", "Alpha.md", "Gamma/one.md")

            assertEquals(listOf("Alpha.md", "beta.md", "Gamma"), project.root.children.map { it.name })
        }

    @Test
    fun `the sidecar and hidden files are not in the binder`() =
        runTest {
            val project = open(".drafts/project.toml", ".git/HEAD", ".DS_Store", "chapter.md")

            assertTrue(project.hasSidecar)
            assertEquals(listOf("chapter.md"), project.root.children.map { it.name })
        }

    @Test
    fun `a nested project is shown and not walked into`() =
        runTest {
            // 13.2: "the nearest .drafts/ wins ... when it finds one someone made by hand it says so
            // rather than silently choosing."
            val project = open("Old/.drafts/project.toml", "Old/scene.fountain", "current.md")

            val old = project.root.children.single { it.name == "Old" }
            assertIs<ProjectNode.Folder>(old)
            assertTrue(old.nestedProject)
            assertTrue(old.children.isEmpty(), "Another project's files were taken into this one")
            assertEquals(listOf(ProjectPath("/Old")), project.nestedProjects)
        }

    @Test
    fun `a folder that cannot be read is shown and says so`() =
        runTest {
            val project = open("Locked/secret.md", "open.md", unreadable = setOf("Locked"))

            val locked = project.root.children.single { it.name == "Locked" }
            assertIs<ProjectNode.Folder>(locked)
            assertTrue(locked.unreadable)
        }

    @Test
    fun `a root that cannot be read is no project`() =
        runTest {
            assertNull(ProjectWalk(FakeListing(emptyList())).open(DocumentRef("/nowhere"), "Nowhere"))
        }

    @Test
    fun `an empty folder is an empty project`() =
        runTest {
            val project = open()

            assertTrue(project.root.children.isEmpty())
            assertEquals(ProjectPath.Root, project.root.path)
        }

    private suspend fun open(
        vararg files: String,
        unreadable: Set<String> = emptySet(),
    ): Project =
        requireNotNull(ProjectWalk(FakeListing(files.toList(), unreadable)).open(DocumentRef(ROOT), "The Salt Road"))

    /** A folder tree from a list of file paths, as a filesystem would list it. */
    private class FakeListing(
        files: List<String>,
        private val unreadable: Set<String> = emptySet(),
    ) : FolderListing {
        private val paths = files.map { "$ROOT/$it" }

        override suspend fun entriesOf(folder: DocumentRef): List<FolderEntry>? {
            val prefix = folder.token + "/"
            val under = paths.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
            val readable =
                folder.token.removePrefix("$ROOT/") !in unreadable && (folder.token == ROOT || under.isNotEmpty())

            return under
                .groupBy { it.substringBefore('/') }
                .map { (name, inside) ->
                    FolderEntry(
                        name,
                        DocumentRef(prefix + name),
                        isFolder = inside.any { '/' in it },
                        modifiedEpochMillis = 0,
                    )
                }.takeIf { readable }
        }
    }

    private companion object {
        const val ROOT = "/projects/salt-road"
    }
}
