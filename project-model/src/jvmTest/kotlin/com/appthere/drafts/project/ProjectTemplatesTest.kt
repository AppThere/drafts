package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderEntry
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.PathFolderEditing
import com.appthere.drafts.platform.files.PathFolderListing
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
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

/** `projects/templates` and the enclosing-project check (`projects/folder-as-project`), on a real folder. */
class ProjectTemplatesTest {
    private val directory: Path = createTempDirectory("drafts-templates")
    private val appData: Path = createTempDirectory("drafts-templates-app")
    private val parent = DocumentRef(directory.toString())
    private val files = ProjectFiles(PathFolderListing(), PathFolderEditing(), PathDocumentStore())
    private val templates = ProjectTemplates(files)

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
        appData.toFile().deleteRecursively()
    }

    @Test
    fun `a novel has its folders, a project file and a gitignore`() =
        runTest {
            val created = templates.create(parent, "The Salt Road", ProjectTemplate.Novel, TemplateFolder::name)

            assertEquals(Outcome.Done, created.outcome)
            val project = directory.resolve("The Salt Road")
            assertEquals(
                setOf("Manuscript", "Notes", "Characters", "Places", ".drafts", ".gitignore"),
                Files.list(project).use { list -> list.map { it.fileName.toString() }.toList().toSet() },
            )
            assertTrue(project.resolve(".drafts/project.toml").exists())
            assertEquals(".drafts/cache/\n.drafts/trash/\n", project.resolve(".gitignore").readText())
        }

    @Test
    fun `a screenplay's folders are in the template's order and none is unsorted`() =
        runTest {
            val created = templates.create(parent, "Salt", ProjectTemplate.Screenplay, TemplateFolder::name)

            val root = opened(created.location!!).project.root
            assertEquals(listOf("Screenplay", "Notes", "Characters", "Beats"), root.children.map { it.name })
            assertTrue(root.children.none { it.unplaced })
        }

    @Test
    fun `template folders are named in the writer's language`() =
        runTest {
            val spanish =
                mapOf(
                    TemplateFolder.Manuscript to "Manuscrito",
                    TemplateFolder.Notes to "Notas",
                    TemplateFolder.Characters to "Personajes",
                    TemplateFolder.Places to "Lugares",
                )

            templates.create(parent, "Novela", ProjectTemplate.Novel) { spanish.getValue(it) }

            assertTrue(directory.resolve("Novela/Manuscrito").exists())
        }

    @Test
    fun `an empty project has its project file and gitignore and nothing else`() =
        runTest {
            templates.create(parent, "Blank", ProjectTemplate.Empty, TemplateFolder::name)

            assertEquals(
                setOf(".drafts", ".gitignore"),
                Files.list(directory.resolve("Blank")).use { l ->
                    l
                        .map {
                            it.fileName.toString()
                        }.toList()
                        .toSet()
                },
            )
            assertEquals(
                listOf("project.toml"),
                Files.list(directory.resolve("Blank/.drafts")).use { l ->
                    l.map { it.fileName.toString() }.toList()
                },
            )
        }

    @Test
    fun `a name already taken makes nothing`() =
        runTest {
            directory.resolve("Notes").createDirectories()
            directory.resolve("Notes/mine.md").writeText("Mine.")

            assertEquals(
                Outcome.Taken,
                templates.create(parent, "Notes", ProjectTemplate.Novel, TemplateFolder::name).outcome,
            )

            assertEquals(
                listOf("mine.md"),
                Files.list(directory.resolve("Notes")).use { l ->
                    l
                        .map {
                            it.fileName.toString()
                        }.toList()
                },
            )
        }

    @Test
    fun `a project inside a project is refused`() =
        runTest {
            directory.resolve("Outer/.drafts").createDirectories()
            directory.resolve("Outer/Notes").createDirectories()

            val inside = DocumentRef(directory.resolve("Outer/Notes").toString())
            assertEquals(
                Outcome.InsideProject,
                templates.create(inside, "Inner", ProjectTemplate.Empty, TemplateFolder::name).outcome,
            )
            assertEquals(
                Outcome.InsideProject,
                templates
                    .create(
                        DocumentRef(directory.resolve("Outer").toString()),
                        "Inner",
                        ProjectTemplate.Empty,
                        TemplateFolder::name,
                    ).outcome,
            )

            assertFalse(directory.resolve("Outer/Notes/Inner").exists())
            assertFalse(directory.resolve("Outer/Inner").exists())
        }

    @Test
    fun `a renamed template folder keeps its place and nothing else changes`() =
        runTest {
            val location = templates.create(parent, "Salt", ProjectTemplate.Novel, TemplateFolder::name).location!!
            val session = session(location).also { it.reconcile(full = true) }

            assertEquals(Outcome.Done, BinderOperations(session).rename(ProjectPath("/Manuscript"), "Draft"))

            assertEquals(
                listOf("Draft", "Notes", "Characters", "Places"),
                opened(location)
                    .project.root.children
                    .map { it.name },
            )
        }

    @Test
    fun `a folder inside a project knows which`() =
        runTest {
            directory.resolve("The Salt Road/.drafts").createDirectories()
            directory.resolve("The Salt Road/Notes/research").createDirectories()

            val found =
                EnclosingProject(
                    files.listing,
                ).of(DocumentRef(directory.resolve("The Salt Road/Notes/research").toString()))

            assertEquals("The Salt Road", assertIs<Enclosing.Inside>(found).project.name)
        }

    @Test
    fun `a folder in no project is in none`() =
        runTest {
            directory.resolve("Loose/Notes").createDirectories()

            assertEquals(
                Enclosing.None,
                EnclosingProject(files.listing).of(DocumentRef(directory.resolve("Loose/Notes").toString())),
            )
        }

    @Test
    fun `a platform that cannot see above a folder cannot tell`() =
        runTest {
            val blind =
                object : FolderListing by files.listing {
                    override suspend fun parentOf(folder: DocumentRef): FolderEntry? = null
                }

            assertEquals(Enclosing.CannotTell, EnclosingProject(blind).of(parent))
        }

    private fun session(location: ProjectLocation) =
        ProjectSession(location, files, ProjectStateStore(PathDocumentStore(), appData.toString()))

    private suspend fun opened(location: ProjectLocation): ProjectView =
        session(location)
            .also {
                it.reconcile(full = true)
            }.view.value!!
}
