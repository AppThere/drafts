package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.PathFolderListing
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `projects.md` 4's `project.toml`, on a real filesystem: "Keep it small and keep entry ordering
 * stable on write" -- and, because it is the reader's file, never written over what it does not
 * understand or over a newer one.
 */
class ProjectManifestTest {
    private val directory: Path = createTempDirectory("drafts-project")
    private val root = DocumentRef(directory.toString())
    private val file = directory.resolve(".drafts/project.toml")
    private val manifest = ProjectManifest(PathFolderListing(), PathDocumentStore())

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a folder with no sidecar reads as the defaults and is not written to`() =
        runTest {
            val state = manifest.read(root)

            assertEquals(ManifestState.Absent, state)
            assertEquals(OrderMode.Manifest, state.file.project.orderMode)
            assertFalse(directory.resolve(".drafts").exists(), "Reading made a sidecar")
        }

    @Test
    fun `the first write makes the sidecar and reads back as written`() =
        runTest {
            val written = manifest.write(root, SALT_ROAD, manifest.read(root))

            assertIs<ManifestWrite.Written>(written)
            assertTrue(file.exists())
            assertEquals(SALT_ROAD, manifest.read(root).file)
        }

    @Test
    fun `the spec's own example reads`() =
        runTest {
            write(SPEC_EXAMPLE)

            val read = assertIs<ManifestState.Read>(manifest.read(root)).file
            assertEquals("The Salt Road", read.project.name)
            assertEquals(
                listOf("Notes", "Characters", "Treatment", "Screenplay"),
                read.project.order
                    .first()
                    .entries,
            )
            assertEquals(listOf("revise", "locked"), read.labels.map { it.id })
            assertEquals(90_000L, read.targets?.project)
            assertEquals("status:todo OR label:revise", read.collections["needs-work"]?.query)
        }

    @Test
    fun `writing what was read changes nothing`() =
        runTest {
            manifest.write(root, SALT_ROAD, manifest.read(root))
            val first = file.readText()

            manifest.write(root, manifest.read(root).file, manifest.read(root))

            assertEquals(first, file.readText())
        }

    @Test
    fun `reordering one folder changes that folder's line and no other`() =
        runTest {
            manifest.write(root, SALT_ROAD, manifest.read(root))
            val before = file.readText().lines()

            val read = manifest.read(root)
            val reordered =
                read.file.copy(
                    project =
                        read.file.project.copy(
                            order =
                                read.file.project.order.map {
                                    if (it.folder == "/Screenplay") it.copy(entries = it.entries.reversed()) else it
                                },
                        ),
                )
            manifest.write(root, reordered, read)
            val after = file.readText().lines()

            assertEquals(before.size, after.size)
            val changed = before.indices.filter { before[it] != after[it] }
            assertEquals(1, changed.size, "Changed lines: ${changed.map { after[it] }}")
            assertTrue("02-the-dock.fountain" in after[changed.single()])
        }

    @Test
    fun `a file with things this version does not know is read and not written over`() =
        runTest {
            // A compile target added by hand before Phase 10 models them: writing the model back
            // would drop it, silently.
            write("$SPEC_EXAMPLE\n[[compile]]\nid = \"screenplay-pdf\"\nkind = \"fountain\"\n")
            val before = file.readText()

            val state = assertIs<ManifestState.NotWritable>(manifest.read(root))
            assertEquals("The Salt Road", state.file.project.name)

            assertIs<ManifestWrite.Refused>(manifest.write(root, state.file, state))
            assertEquals(before, file.readText())
        }

    @Test
    fun `a file that is not readable TOML is kept and not written over`() =
        runTest {
            write("[project\nname = ")

            val state = assertIs<ManifestState.Invalid>(manifest.read(root))
            assertIs<ManifestWrite.Refused>(manifest.write(root, ProjectFile(), state))
            assertEquals("[project\nname = ", file.readText())
        }

    @Test
    fun `a file changed since it was read is not written over`() =
        runTest {
            // A git pull, or another device's sync, between reading and writing.
            manifest.write(root, SALT_ROAD, manifest.read(root))
            val read = manifest.read(root)
            file.writeText(file.readText() + "\n[targets]\nsession = 500\n")
            val theirs = file.readText()

            assertEquals(ManifestWrite.Conflict, manifest.write(root, read.file, read))
            assertEquals(theirs, file.readText())
        }

    @Test
    fun `a file that appeared since the project was read is not written over`() =
        runTest {
            val read = manifest.read(root)
            write(SPEC_EXAMPLE)

            assertEquals(ManifestWrite.Conflict, manifest.write(root, SALT_ROAD, read))
            assertEquals(SPEC_EXAMPLE, file.readText())
        }

    private fun write(text: String) {
        directory.resolve(".drafts").createDirectories()
        file.writeText(text)
    }

    private companion object {
        val SALT_ROAD =
            ProjectFile(
                project =
                    ProjectSettings(
                        name = "The Salt Road",
                        order =
                            listOf(
                                FolderOrder("/", listOf("Notes", "Characters", "Screenplay")),
                                FolderOrder("/Screenplay", listOf("01-cold-open.fountain", "02-the-dock.fountain")),
                            ),
                    ),
                labels = listOf(Label("revise", "Needs revision", "#C1554D")),
                statuses = listOf(Status("todo", "To do"), Status("done", "Done")),
                targets = Targets(project = 90_000),
            )

        /** `projects.md` 4's example, as the spec now gives it. */
        val SPEC_EXAMPLE =
            """
            [project]
            name = "The Salt Road"
            schema = 1
            orderMode = "manifest"
            defaultKind = "markdown"

            [[project.order]]
            folder = "/"
            entries = ["Notes", "Characters", "Treatment", "Screenplay"]

            [[project.order]]
            folder = "/Screenplay"
            entries = ["01-cold-open.fountain", "02-the-dock.fountain", "03-marla-arrives.fountain"]

            [[labels]]
            id = "revise"
            name = "Needs revision"
            color = "#C1554D"

            [[labels]]
            id = "locked"
            name = "Locked"
            color = "#4D7EA8"

            [[statuses]]
            id = "todo"
            name = "To do"

            [targets]
            project = 90000
            session = 1000

            [collections.needs-work]
            name = "Needs work"
            type = "saved-search"
            query = "status:todo OR label:revise"
            """.trimIndent() + "\n"
    }
}
