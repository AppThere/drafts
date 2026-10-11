package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.intents.DocumentKind
import kotlin.jvm.JvmInline

/**
 * A project: `projects.md` 1's "ordinary folder of ordinary files with their real names and
 * extensions". The folder tree *is* the binder.
 *
 * [hasSidecar] is whether `.drafts/` exists. 13.1: "A folder becomes a project implicitly" -- any
 * folder opens as one, and the sidecar is made only when something needs it. Opening one never
 * writes.
 */
data class Project(
    val name: String,
    val root: ProjectNode.Folder,
    val hasSidecar: Boolean,
) {
    /**
     * Folders inside this one that have a `.drafts/` of their own. 13.2: "Nesting is unsupported;
     * the nearest `.drafts/` wins ... when it finds one someone made by hand it says so rather than
     * silently choosing." Such a folder is shown, and not walked: its files are another project's.
     */
    val nestedProjects: List<ProjectPath> get() = root.folders().filter { it.nestedProject }.map { it.path }
}

/**
 * Where something is in a project, from its root: `/Screenplay/02-the-dock.fountain`. The form
 * `project.toml` keys its order by (`projects.md` 4), and the same on every platform whatever the
 * file's own location looks like.
 */
@JvmInline
value class ProjectPath(
    val value: String,
) {
    fun child(name: String): ProjectPath = ProjectPath(if (value == ROOT) "/$name" else "$value/$name")

    /** The folder this is in; the root is its own. */
    val parent: ProjectPath get() = ProjectPath(value.substringBeforeLast('/').ifEmpty { ROOT })

    /** The last part of the path: a file's or folder's own name. Empty for the root. */
    val name: String get() = value.substringAfterLast('/')

    /** Whether this is [folder] or somewhere inside it. */
    fun isWithin(folder: ProjectPath): Boolean =
        folder == Root || this == folder || value.startsWith(folder.value + "/")

    override fun toString(): String = value

    companion object {
        private const val ROOT = "/"
        val Root = ProjectPath(ROOT)
    }
}

/** One thing in the binder. */
sealed interface ProjectNode {
    val name: String
    val path: ProjectPath

    /** Where it is on disk; null only for [Missing], which is not anywhere. */
    val ref: DocumentRef?

    /**
     * In a folder the writer has ordered, and not placed in that order: `projects.md` 3's "new"
     * marker, for a file added from outside -- a pull, a sync, a drop from a file manager.
     */
    val unplaced: Boolean

    /**
     * A folder, and what is in it.
     *
     * [unreadable] is a folder the listing could not open -- not permitted, or gone mid-walk. It is
     * shown, with nothing in it and that said, rather than dropped: a folder that vanished from the
     * binder would read as its contents having been deleted.
     *
     * [loading] is a folder the walk has found and not listed yet (`projects/folder-as-project`, "A
     * project is shown while it is still being walked").
     */
    data class Folder(
        override val name: String,
        override val path: ProjectPath,
        override val ref: DocumentRef,
        val children: List<ProjectNode>,
        val nestedProject: Boolean = false,
        val unreadable: Boolean = false,
        override val unplaced: Boolean = false,
        val loading: Boolean = false,
    ) : ProjectNode

    /** A Markdown or Fountain file: something Drafts opens and edits (`projects.md` 5). */
    data class Document(
        override val name: String,
        override val path: ProjectPath,
        override val ref: DocumentRef,
        val kind: DocumentKind,
        override val unplaced: Boolean = false,
        /** When the listing last saw it change, which is what tells reconciliation to look again. */
        val modifiedEpochMillis: Long = 0,
    ) : ProjectNode

    /**
     * Anything else: a PDF, an image, reference material. 13.3: "Non-text files are shown and
     * opened externally ... Drafts never edits them."
     */
    data class Attachment(
        override val name: String,
        override val path: ProjectPath,
        override val ref: DocumentRef,
        override val unplaced: Boolean = false,
        val modifiedEpochMillis: Long = 0,
    ) : ProjectNode

    /**
     * Gone from disk and still remembered: `projects.md` 9's "missing" state, "shown greyed in the
     * binder". Placed where the order had it, and never dropped from the order by the application
     * -- "keep the entry, mark missing, never silently drop it" -- since the file may only be away:
     * a sync not finished, a drive not mounted, a rename to be relinked.
     */
    data class Missing(
        override val name: String,
        override val path: ProjectPath,
    ) : ProjectNode {
        override val ref: DocumentRef? = null
        override val unplaced: Boolean = false
    }
}

/** Every folder at or below this one, this one first. */
fun ProjectNode.Folder.folders(): List<ProjectNode.Folder> =
    listOf(this) + children.filterIsInstance<ProjectNode.Folder>().flatMap { it.folders() }

/** Every document at or below this folder, in the order the tree holds them. */
fun ProjectNode.Folder.documents(): List<ProjectNode.Document> =
    children.flatMap { child ->
        when (child) {
            is ProjectNode.Document -> listOf(child)
            is ProjectNode.Folder -> child.documents()
            is ProjectNode.Attachment, is ProjectNode.Missing -> emptyList()
        }
    }

/** Every file at or below this folder, documents and attachments, in the order the tree holds them. */
fun ProjectNode.Folder.files(): List<ProjectNode> =
    children.flatMap { child ->
        when (child) {
            is ProjectNode.Folder -> child.files()
            is ProjectNode.Document, is ProjectNode.Attachment -> listOf(child)
            is ProjectNode.Missing -> emptyList()
        }
    }

/** The folder at [path], if the tree has one there. */
fun ProjectNode.Folder.folderAt(path: ProjectPath): ProjectNode.Folder? = folders().firstOrNull { it.path == path }

/** The node at [path], if the tree has one there. */
fun ProjectNode.Folder.nodeAt(path: ProjectPath): ProjectNode? =
    folderAt(path) ?: folderAt(path.parent)?.children?.firstOrNull { it.path == path }

/**
 * The project with each of [missing] shown where it was (`projects/reconciliation`, "shown in its
 * place in the binder as missing"): in its folder, among the names as the walk sorts them, for the
 * ordering to place like any other entry. One whose folder has gone too is not shown; its folder is.
 */
internal fun Project.withMissing(missing: List<ProjectPath>): Project =
    if (missing.isEmpty()) this else copy(root = root.withMissing(missing))

private fun ProjectNode.Folder.withMissing(missing: List<ProjectPath>): ProjectNode.Folder {
    val here = missing.filter { it.parent == path && children.none { child -> child.name == it.name } }
    val children = children.map { if (it is ProjectNode.Folder) it.withMissing(missing) else it }
    return copy(
        children =
            (children + here.map { ProjectNode.Missing(it.name, it) })
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
    )
}
