package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderEditing
import com.appthere.drafts.platform.files.FolderOutcome
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.PathFolderEditing
import com.appthere.drafts.platform.files.PathFolderListing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

/**
 * A project on a real folder, with its own app-private storage, for the tests of what a
 * [ProjectSession] does to it. Call [cleanUp] after each test.
 */
class ProjectFixture {
    val directory: Path = createTempDirectory("drafts-session")
    private val appData: Path = createTempDirectory("drafts-session-app")
    val root = DocumentRef(directory.toString())
    private val listing = PathFolderListing()
    private val store = PathDocumentStore()

    fun cleanUp() {
        directory.toFile().deleteRecursively()
        appData.toFile().deleteRecursively()
    }

    /** A session on the project, reconciled in full as on opening. */
    suspend fun opened(editing: FolderEditing = PathFolderEditing()): ProjectSession =
        ProjectSession(ProjectLocation(root, "Salt"), files(editing), states(), now = {
            NOW
        }).also { it.reconcile(full = true) }

    fun files(editing: FolderEditing = PathFolderEditing()) = ProjectFiles(listing, editing, store)

    fun states() = ProjectStateStore(PathDocumentStore(), appData.toString())

    /** `project.toml` as [file] says, written before the session reads it. */
    suspend fun order(file: ProjectFile) {
        ProjectManifest(listing, PathFolderEditing(), store).write(root, file, ManifestState.Absent)
    }

    fun file(
        path: String,
        text: String,
    ) {
        val file = directory.resolve(path)
        file.parent.createDirectories()
        file.writeText(text)
    }

    /** The folder's names on disk, sorted. */
    fun names(folder: String): List<String> =
        Files.list(directory.resolve(folder)).use { list -> list.map { it.fileName.toString() }.sorted().toList() }

    /** The folder as a freshly opened session's binder shows it. */
    suspend fun shown(folder: String): List<String> {
        val view = opened().view.value!!
        val path = if (folder.isEmpty()) ProjectPath.Root else ProjectPath("/$folder")
        return view.project.root
            .folderAt(path)!!
            .children
            .filterNot { it.name == ".keep" }
            .map { it.name }
    }

    /** Every file in the project, with its contents. */
    fun contents(): Map<String, String> =
        Files.walk(directory).use { walk ->
            walk.filter { it.isRegularFile() }.toList().associate {
                it
                    .relativeTo(
                        directory,
                    ).toString() to it.readText()
            }
        }

    /** Folder editing that refuses its [nth] change, as a provider or a disk might. */
    class FailingOn(
        private val nth: Int,
        private val real: FolderEditing,
    ) : FolderEditing by real {
        private var count = 0

        private fun refused(): Boolean = ++count == nth

        override suspend fun rename(
            ref: DocumentRef,
            name: String,
        ): FolderOutcome = if (refused()) FolderOutcome.Failed("refused") else real.rename(ref, name)

        override suspend fun move(
            ref: DocumentRef,
            from: DocumentRef,
            to: DocumentRef,
        ): FolderOutcome = if (refused()) FolderOutcome.Failed("refused") else real.move(ref, from, to)
    }

    companion object {
        val SCREENPLAY = ProjectPath("/Screenplay")
        const val NOW = 1_790_000_000_000L
    }
}
