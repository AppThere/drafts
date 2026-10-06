package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderEntry
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.intents.DocumentKind

/**
 * Opens a folder as a project: `projects.md` 14's first step, "Open a folder, build a tree, no
 * metadata, no order."
 *
 * Reads only. 13.1: "A folder that is only browsed is never written to."
 *
 * Within a folder, things are in alphabetical order, folders and files together, ignoring case.
 * That is 3's third tier, and until `project.toml` is read it is the only one.
 *
 * What is left out: the project's own `.drafts/`, and anything whose name begins with a full stop
 * -- `.git`, `.DS_Store`, an editor's swap file. The spec does not say; these are the files every
 * file manager hides by default, they are not the writer's, and a binder that showed `.git` would
 * invite dragging it somewhere.
 */
class ProjectWalk(
    private val listing: FolderListing,
) {
    /** The project at [root], called [name], or null if [root] itself cannot be read. */
    suspend fun open(
        root: DocumentRef,
        name: String,
    ): Project? {
        val entries = listing.entriesOf(root) ?: return null

        return Project(
            name = name,
            root = folder(name, ProjectPath.Root, root, entries),
            hasSidecar = entries.any { it.isFolder && it.name == SIDECAR },
        )
    }

    private suspend fun folder(
        name: String,
        path: ProjectPath,
        ref: DocumentRef,
        entries: List<FolderEntry>,
    ): ProjectNode.Folder {
        val children =
            entries
                .filterNot { it.name.startsWith(HIDDEN) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { entry -> node(entry, path.child(entry.name)) }

        return ProjectNode.Folder(name = name, path = path, ref = ref, children = children)
    }

    private suspend fun node(
        entry: FolderEntry,
        path: ProjectPath,
    ): ProjectNode = if (entry.isFolder) folderNode(entry, path) else fileNode(entry, path)

    /** A document if its extension names a kind (`projects.md` 5), and an attachment otherwise. */
    private fun fileNode(
        entry: FolderEntry,
        path: ProjectPath,
    ): ProjectNode =
        DocumentKind.of(entry.name)?.let { kind -> ProjectNode.Document(entry.name, path, entry.ref, kind) }
            ?: ProjectNode.Attachment(entry.name, path, entry.ref)

    /** A folder and what is in it -- unless it cannot be read, or is another project's. */
    private suspend fun folderNode(
        entry: FolderEntry,
        path: ProjectPath,
    ): ProjectNode.Folder {
        val inside = listing.entriesOf(entry.ref)

        return when {
            inside == null -> {
                ProjectNode.Folder(entry.name, path, entry.ref, emptyList(), unreadable = true)
            }

            inside.any { it.isFolder && it.name == SIDECAR } -> {
                ProjectNode.Folder(entry.name, path, entry.ref, emptyList(), nestedProject = true)
            }

            else -> {
                folder(entry.name, path, entry.ref, inside)
            }
        }
    }

    private companion object {
        /** `projects.md` 4: the sidecar every project keeps its order and metadata in. */
        const val SIDECAR = ".drafts"

        const val HIDDEN = "."
    }
}
