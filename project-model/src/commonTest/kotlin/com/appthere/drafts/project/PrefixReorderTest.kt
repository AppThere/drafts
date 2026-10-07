package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderRenaming
import com.appthere.drafts.platform.files.RenameOutcome
import com.appthere.drafts.platform.intents.DocumentKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `projects.md` 3's `prefix` mode: "Reordering renames files `01-`, `02-`; order is visible outside
 * the app" -- and, because these are the reader's files, all or nothing, and never over another.
 */
class PrefixReorderTest {
    @Test
    fun `a reorder numbers each entry in the order given`() =
        runTest {
            val folder = Folder("the-dock.fountain", "cold-open.fountain", "Notes")

            val outcome =
                PrefixReorder(
                    folder,
                ).reorder(folder.nodes("cold-open.fountain", "the-dock.fountain", "Notes"))

            assertIs<PrefixOutcome.Reordered>(outcome)
            assertEquals(setOf("01-cold-open.fountain", "02-the-dock.fountain", "03-Notes"), folder.names)
        }

    @Test
    fun `a prefix already there is replaced and not added to`() {
        assertEquals("02-the-dock.fountain", prefixed("7 the-dock.fountain", 2, 2))
        assertEquals("02-the-dock.fountain", prefixed("01-the-dock.fountain", 2, 2))
        assertEquals("002-1984.md", prefixed("1984.md", 2, 3))
    }

    @Test
    fun `only the entries whose names change are renamed`() =
        runTest {
            val folder = Folder("01-a.md", "02-b.md", "c.md")

            val outcome = PrefixReorder(folder).reorder(folder.nodes("01-a.md", "02-b.md", "c.md"))

            assertEquals(PrefixOutcome.Reordered(mapOf("c.md" to "03-c.md")), outcome)
            assertEquals(1, folder.renames)
        }

    @Test
    fun `a folder of a hundred entries is numbered three digits wide`() =
        runTest {
            val names = (1..100).map { "scene-$it.fountain" }
            val folder = Folder(*names.toTypedArray())

            PrefixReorder(folder).reorder(folder.nodes(*names.toTypedArray()))

            assertTrue("001-scene-1.fountain" in folder.names)
            assertTrue("100-scene-100.fountain" in folder.names)
        }

    @Test
    fun `two entries trading places go through temporary names`() =
        runTest {
            // 02-a.md becomes 01-a.md, which 01-a.md has until it becomes 02-a.md.
            val folder = Folder("01-a.md", "02-a.md")

            val outcome = PrefixReorder(folder).reorder(folder.nodes("02-a.md", "01-a.md"))

            assertEquals(PrefixOutcome.Reordered(mapOf("02-a.md" to "01-a.md", "01-a.md" to "02-a.md")), outcome)
            assertEquals(mapOf("01-a.md" to "second", "02-a.md" to "first"), folder.contents)
        }

    @Test
    fun `a rename that fails undoes the ones before it`() =
        runTest {
            val folder = Folder("a.md", "b.md", "c.md", failAt = 3)

            val outcome = PrefixReorder(folder).reorder(folder.nodes("a.md", "b.md", "c.md"))

            val failed = assertIs<PrefixOutcome.NotReordered>(outcome)
            assertTrue(failed.stranded.isEmpty())
            assertEquals(setOf("a.md", "b.md", "c.md"), folder.names)
        }

    @Test
    fun `a swap that fails half way is put back as it was`() =
        runTest {
            val folder = Folder("01-a.md", "02-a.md", failAt = 4)

            val outcome = PrefixReorder(folder).reorder(folder.nodes("02-a.md", "01-a.md"))

            assertIs<PrefixOutcome.NotReordered>(outcome)
            assertEquals(mapOf("01-a.md" to "first", "02-a.md" to "second"), folder.contents)
        }

    @Test
    fun `a name taken outside the reorder is never written over`() =
        runTest {
            // Something called 01-b.md that is not one of the entries being reordered.
            val folder = Folder("b.md", "a.md", "01-b.md")

            val outcome = PrefixReorder(folder).reorder(folder.nodes("b.md", "a.md"))

            assertIs<PrefixOutcome.NotReordered>(outcome)
            assertEquals(setOf("b.md", "a.md", "01-b.md"), folder.names)
        }

    @Test
    fun `a rename that cannot be undone is said by name`() =
        runTest {
            val folder = Folder("a.md", "b.md", failAt = 2, failUndo = true)

            val failed =
                assertIs<PrefixOutcome.NotReordered>(PrefixReorder(folder).reorder(folder.nodes("a.md", "b.md")))

            assertEquals(listOf("01-a.md"), failed.stranded)
        }

    @Test
    fun `the order in project_toml follows the renames`() {
        val file =
            ProjectFile(project = ProjectSettings(order = listOf(FolderOrder("/", listOf("b.md", "a.md", "gone.md")))))

        val renamed = file.renamed(ProjectPath.Root, mapOf("b.md" to "01-b.md", "a.md" to "02-a.md"))

        assertEquals(
            listOf("01-b.md", "02-a.md", "gone.md"),
            renamed.project.order
                .single()
                .entries,
        )
    }

    /**
     * A folder of named files in memory. The files' contents are which one each was at the start --
     * "first", "second" -- so a test can see where each one ended up. [failAt] makes that rename, by
     * count from one, fail; [failUndo] makes every rename after it fail too.
     */
    private class Folder(
        vararg names: String,
        private val failAt: Int? = null,
        private val failUndo: Boolean = false,
    ) : FolderRenaming {
        val contents: MutableMap<String, String> =
            names
                .mapIndexed {
                    index,
                    name,
                    ->
                    name to ORDINALS[index.coerceAtMost(ORDINALS.lastIndex)]
                }.toMap()
                .toMutableMap()
        val names: Set<String> get() = contents.keys
        var renames = 0
            private set
        private var calls = 0
        private var failed = false

        fun nodes(vararg names: String): List<ProjectNode> =
            names.map { ProjectNode.Document(it, ProjectPath.Root.child(it), DocumentRef(it), DocumentKind.Markdown) }

        override suspend fun rename(
            ref: DocumentRef,
            name: String,
        ): RenameOutcome =
            when {
                ++calls == failAt || (failed && failUndo) -> {
                    failed = true
                    RenameOutcome.Failed("refused")
                }

                name in contents -> {
                    RenameOutcome.Taken
                }

                else -> {
                    contents[name] = contents.remove(ref.token) ?: return RenameOutcome.Failed("gone")
                    renames++
                    RenameOutcome.Renamed(DocumentRef(name))
                }
            }

        private companion object {
            val ORDINALS = listOf("first", "second", "third", "fourth")
        }
    }
}
