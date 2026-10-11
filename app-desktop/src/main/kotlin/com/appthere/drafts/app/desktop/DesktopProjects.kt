package com.appthere.drafts.app.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appthere.drafts.app.ProjectActions
import com.appthere.drafts.app.ProjectEntry
import com.appthere.drafts.app.templateFolderNames
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.breadcrumb
import com.appthere.drafts.i18n.resources.cancel
import com.appthere.drafts.i18n.resources.document_in_project
import com.appthere.drafts.i18n.resources.document_in_project_title
import com.appthere.drafts.i18n.resources.folder_in_project
import com.appthere.drafts.i18n.resources.open_folder_title
import com.appthere.drafts.i18n.resources.open_project
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathFolderEditing
import com.appthere.drafts.platform.files.PathFolderListing
import com.appthere.drafts.platform.files.chooseFolder
import com.appthere.drafts.platform.files.epochMillis
import com.appthere.drafts.platform.files.folderWithin
import com.appthere.drafts.platform.files.isFolder
import com.appthere.drafts.platform.files.nameOfPath
import com.appthere.drafts.project.Created
import com.appthere.drafts.project.Enclosing
import com.appthere.drafts.project.EnclosingProject
import com.appthere.drafts.project.Outcome
import com.appthere.drafts.project.ProjectFiles
import com.appthere.drafts.project.ProjectLocation
import com.appthere.drafts.project.ProjectTemplate
import com.appthere.drafts.project.ProjectTemplates
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.awt.Frame
import javax.swing.JOptionPane

/**
 * The desktop's projects (`projects/project-windows`): which have windows, which window should come
 * forward, whether the projects window is showing, and the ways a project gets opened -- a folder
 * chosen, a template filled in, a recent one picked, last session's restored.
 *
 * A project has one window. Asking to open one that is already open brings its window forward.
 */
@Stable
internal class DesktopProjects(
    private val stores: Stores,
) {
    /** The projects with windows, in the order they were opened. */
    val open = mutableStateListOf<ProjectEntry>()

    /** The project whose window should come to the front, once. */
    var forward by mutableStateOf<String?>(null)

    var home by mutableStateOf(false)

    /** Where a new project is to go, once its folder has been chosen and before it has a name. */
    var newIn by mutableStateOf<String?>(null)

    /** Last session's open projects, with what their windows showed. */
    suspend fun restore(): List<ProjectEntry> =
        stores.projects
            .open()
            .filter { isFolder(it.token) }
            .also { open += it }

    /** The documents open projects hold in their editors, which are not document windows of their own. */
    fun heldDocuments(): Set<String> = open.mapNotNull { it.document }.toSet()

    suspend fun open(
        folder: String,
        name: String = nameOfPath(folder),
    ) {
        val already = open.firstOrNull { it.token == folder }
        if (already != null) {
            forward = folder
            return
        }
        stores.projects.opened(DocumentRef(folder), name, epochMillis())
        open += stores.projects.entries().first { it.token == folder }
        home = false
    }

    /**
     * A folder the writer chose, opened as a project -- unless it is inside one, which they are told,
     * and offered that one instead (`projects/folder-as-project`, "A folder inside a project says so").
     */
    suspend fun openFolder(parent: Frame?) {
        val folder = chooseFolder(parent, getString(Res.string.open_folder_title)) ?: return
        when (val enclosing = EnclosingProject(PathFolderListing()).of(DocumentRef(folder))) {
            is Enclosing.Inside -> {
                val name = nameOfPath(folder)
                val offer = getString(Res.string.folder_in_project, name, enclosing.project.name)
                if (agreed(parent, offer)) open(enclosing.project.ref.token, enclosing.project.name)
            }

            Enclosing.None, Enclosing.CannotTell -> {
                open(folder)
            }
        }
    }

    /**
     * A document opened directly, from the file manager or the command line: if it is inside a
     * project that is not open, the writer is offered that project too -- never opened unasked.
     */
    suspend fun offerFor(file: String) {
        val enclosing = EnclosingProject(PathFolderListing()).of(DocumentRef(file)) as? Enclosing.Inside ?: return
        if (open.any { it.token == enclosing.project.ref.token }) return
        val name = nameOfPath(file)
        if (agreed(null, getString(Res.string.document_in_project, name, enclosing.project.name))) {
            open(enclosing.project.ref.token, enclosing.project.name)
        }
    }

    /** The folder a new project goes in, chosen first; its name and template are asked next. */
    suspend fun chooseNewProjectFolder(parent: Frame?) {
        newIn = chooseFolder(parent, getString(Res.string.open_folder_title))
        home = newIn != null || home
    }

    suspend fun create(
        name: String,
        template: ProjectTemplate,
    ): Created {
        val parent = newIn ?: return Created(Outcome.Failed("No folder chosen"))
        val files = ProjectFiles(PathFolderListing(), PathFolderEditing(), stores.files)
        val created = ProjectTemplates(files).create(DocumentRef(parent), name, template, templateFolderNames())
        created.location?.let { open(it.ref.token, it.name) }
        if (created.location != null) newIn = null
        return created
    }

    /** [entry]'s window now has the document [documentId] in its editor, which comes back with it. */
    suspend fun holding(
        entry: ProjectEntry,
        documentId: String,
    ) {
        open.replaceAll { if (it.token == entry.token) it.copy(document = documentId) else it }
        val now = open.first { it.token == entry.token }
        stores.projects.remember(DocumentRef(entry.token), now.expanded, now.selected, documentId)
    }

    suspend fun pin(
        entry: ProjectEntry,
        pinned: Boolean,
    ) = stores.projects.pin(DocumentRef(entry.token), pinned)

    /** Off the recent list: the folder is not touched. */
    suspend fun forget(entry: ProjectEntry) = stores.projects.remove(DocumentRef(entry.token))

    suspend fun closed(entry: ProjectEntry) {
        open.removeAll { it.token == entry.token }
        stores.projects.closed(DocumentRef(entry.token))
        stores.projectSessions.close(DocumentRef(entry.token))
    }

    /** The open project a document is in, if one is, and where in it. */
    fun containing(file: String?): Pair<ProjectEntry, String>? {
        if (file == null) return null
        return open
            .mapNotNull { project -> folderWithin(file, project.token)?.let { project to it } }
            .maxByOrNull { (project, _) -> project.token.length }
    }

    fun location(entry: ProjectEntry) = ProjectLocation(DocumentRef(entry.token), entry.name)

    /** What a document window adds for projects: the ways to them, and its own project if it has one. */
    fun actionsFor(
        file: String?,
        parent: () -> Frame?,
        launch: (suspend () -> Unit) -> Unit,
    ): DocumentInProject {
        val inside = containing(file)
        val actions =
            ProjectActions(
                openFolder = { launch { openFolder(parent()) } },
                recent = { home = true },
                showProject = inside?.let { (project, _) -> { forward = project.token } },
            )
        return DocumentInProject(inside?.first?.name, inside?.second, actions)
    }
}

/** A document window's project, if it was opened from one: its name, the folder within it, and the ways to it. */
internal class DocumentInProject(
    val project: String? = null,
    val folder: String? = null,
    val actions: ProjectActions? = null,
) {
    /** "dock.fountain — The Salt Road › Screenplay", or just the name outside a project. */
    @Composable
    fun title(name: String): String {
        val project = project ?: return name
        val place =
            folder?.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.breadcrumb, project, it) } ?: project
        return stringResource(Res.string.document_in_project_title, name, place)
    }
}

/**
 * Asked with the platform's own dialog, which is modal like the file pickers it follows: the offer
 * is about what to open, and nothing else can sensibly happen until it is answered.
 */
private suspend fun agreed(
    parent: Frame?,
    message: String,
): Boolean {
    val yes = getString(Res.string.open_project)
    val no = getString(Res.string.cancel)
    val answer =
        JOptionPane.showOptionDialog(
            parent,
            message,
            yes,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            arrayOf(yes, no),
            no,
        )
    return answer == 0
}
