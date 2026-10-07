package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.intents.DocumentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `projects.md` 3: "The one thing the filesystem can't express and a writer can't live without.
 * Chapter 12 does not come after Chapter 1 alphabetically."
 */
class OrderingTest {
    @Test
    fun `the manifest's order comes first`() {
        val ordered = project("ezra.md", "marla.md", "notes.md").ordered(order("/" to listOf("marla.md", "ezra.md")))

        assertEquals(listOf("marla.md", "ezra.md", "notes.md"), ordered.names())
    }

    @Test
    fun `numbered files follow their numbers and not the alphabet`() {
        val ordered =
            project(
                "10-the-end.fountain",
                "2-the-dock.fountain",
                "01-cold-open.fountain",
            ).ordered(ProjectFile())

        assertEquals(listOf("01-cold-open.fountain", "2-the-dock.fountain", "10-the-end.fountain"), ordered.names())
    }

    @Test
    fun `the manifest then the numbers then the alphabet`() {
        val ordered =
            project("zebra.md", "apple.md", "02-two.md", "01-one.md", "chosen.md")
                .ordered(order("/" to listOf("chosen.md")))

        assertEquals(listOf("chosen.md", "01-one.md", "02-two.md", "apple.md", "zebra.md"), ordered.names())
    }

    @Test
    fun `a title that starts with a number is not a prefix`() {
        assertNull(numericPrefixOf("1984.md"))
        assertNull(numericPrefixOf("2049"))
        assertEquals(3, numericPrefixOf("03_scene.fountain"))
        assertEquals(7, numericPrefixOf("7 Days.md"))
        assertEquals(3, numericPrefixOf("3. The End.md"))
    }

    @Test
    fun `a file the writer has not placed is marked new and never hidden`() {
        // 3: "A file that appears in the folder but not in the manifest is never hidden. It sorts
        // to the end of its folder and shows a subtle 'new' marker until the user places it."
        val ordered =
            project(
                "marla.md",
                "ezra.md",
                "pulled-in.md",
            ).ordered(order("/" to listOf("marla.md", "ezra.md")))

        assertEquals(
            "pulled-in.md",
            ordered.root.children
                .last()
                .name,
        )
        assertTrue(
            ordered.root.children
                .last()
                .unplaced,
        )
        assertFalse(
            ordered.root.children
                .first()
                .unplaced,
        )
    }

    @Test
    fun `nothing is new in a folder that has never been ordered`() {
        val ordered = project("a.md", "b.md").ordered(ProjectFile())

        assertTrue(ordered.root.children.none { it.unplaced })
    }

    @Test
    fun `each folder takes its own order`() {
        val folder = ProjectPath("/Screenplay")
        val tree =
            ProjectNode.Folder(
                "Salt",
                ProjectPath.Root,
                DocumentRef("/"),
                listOf(
                    ProjectNode.Folder(
                        "Screenplay",
                        folder,
                        DocumentRef("/Screenplay"),
                        nodes(folder, "a.fountain", "b.fountain"),
                    ),
                    document(ProjectPath.Root, "notes.md"),
                ),
            )

        val ordered =
            Project("Salt", tree, hasSidecar = true).ordered(order("/Screenplay" to listOf("b.fountain", "a.fountain")))

        val screenplay =
            ordered.root.children
                .filterIsInstance<ProjectNode.Folder>()
                .single()
        assertEquals(listOf("b.fountain", "a.fountain"), screenplay.children.map { it.name })
    }

    @Test
    fun `a reorder writes the folder's order`() {
        val file = ProjectFile().reordered(ProjectPath.Root, listOf("marla.md", "ezra.md"))

        assertEquals(listOf(FolderOrder("/", listOf("marla.md", "ezra.md"))), file.project.order)
    }

    @Test
    fun `a reorder keeps the names of files that are missing for now`() {
        // 9: a manifest entry for a path that no longer exists is kept, "never silently drop it".
        val file =
            order("/" to listOf("a.md", "gone.md", "b.md"))
                .reordered(ProjectPath.Root, listOf("b.md", "a.md"))

        assertEquals(
            listOf("b.md", "a.md", "gone.md"),
            file.project.order
                .single()
                .entries,
        )
    }

    @Test
    fun `a folder ordered for the first time goes after the others`() {
        // So the lines already in project.toml stay where they are.
        val file =
            order("/" to listOf("Notes"), "/Notes" to listOf("x.md"))
                .reordered(ProjectPath("/Characters"), listOf("marla.md"))

        assertEquals(listOf("/", "/Notes", "/Characters"), file.project.order.map { it.folder })
    }

    @Test
    fun `reordering a folder leaves the others as they were`() {
        val before = order("/" to listOf("Notes", "Screenplay"), "/Notes" to listOf("x.md", "y.md"))

        val after = before.reordered(ProjectPath("/Notes"), listOf("y.md", "x.md"))

        assertEquals(before.project.order.first(), after.project.order.first())
        assertEquals(
            listOf("y.md", "x.md"),
            after.project.order
                .last()
                .entries,
        )
    }

    private fun Project.names(): List<String> = root.children.map { it.name }

    private fun order(vararg folders: Pair<String, List<String>>): ProjectFile =
        ProjectFile(
            project =
                ProjectSettings(
                    order =
                        folders.map { (folder, entries) ->
                            FolderOrder(folder, entries)
                        },
                ),
        )

    /** A flat project of [names], in the alphabetical order the walk gives. */
    private fun project(vararg names: String): Project =
        Project(
            "Salt",
            ProjectNode.Folder(
                "Salt",
                ProjectPath.Root,
                DocumentRef("/"),
                nodes(ProjectPath.Root, *names).sortedBy {
                    it.name.lowercase()
                },
            ),
            hasSidecar = true,
        )

    private fun nodes(
        folder: ProjectPath,
        vararg names: String,
    ): List<ProjectNode> = names.map { document(folder, it) }

    private fun document(
        folder: ProjectPath,
        name: String,
    ): ProjectNode =
        ProjectNode.Document(
            name,
            folder.child(name),
            DocumentRef(folder.child(name).value),
            DocumentKind.of(name) ?: DocumentKind.Markdown,
        )
}
