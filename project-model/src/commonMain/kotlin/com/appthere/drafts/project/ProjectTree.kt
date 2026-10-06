package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.intents.DocumentKind

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
    val ref: DocumentRef

    /**
     * A folder, and what is in it.
     *
     * [unreadable] is a folder the listing could not open -- not permitted, or gone mid-walk. It is
     * shown, with nothing in it and that said, rather than dropped: a folder that vanished from the
     * binder would read as its contents having been deleted.
     */
    data class Folder(
        override val name: String,
        override val path: ProjectPath,
        override val ref: DocumentRef,
        val children: List<ProjectNode>,
        val nestedProject: Boolean = false,
        val unreadable: Boolean = false,
    ) : ProjectNode

    /** A Markdown or Fountain file: something Drafts opens and edits (`projects.md` 5). */
    data class Document(
        override val name: String,
        override val path: ProjectPath,
        override val ref: DocumentRef,
        val kind: DocumentKind,
    ) : ProjectNode

    /**
     * Anything else: a PDF, an image, reference material. 13.3: "Non-text files are shown and
     * opened externally ... Drafts never edits them."
     */
    data class Attachment(
        override val name: String,
        override val path: ProjectPath,
        override val ref: DocumentRef,
    ) : ProjectNode
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
            is ProjectNode.Attachment -> emptyList()
        }
    }
