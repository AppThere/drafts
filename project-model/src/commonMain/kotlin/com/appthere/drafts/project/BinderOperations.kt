package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderOutcome

/**
 * What the writer can do to a project from the binder (`projects/binder`): create, rename, move,
 * reorder, and take a missing name out of the order. Each runs as one [ProjectSession] change, all
 * of it or none of it, with `project.toml` written first and snapshots moved last ([Carry]).
 *
 * A name already in use is refused from the binder's own picture of the folder before anything is
 * asked of the platform, which is what lets the writer be told the name is taken rather than only
 * that something failed.
 */
class BinderOperations(
    private val session: ProjectSession,
) {
    private val files get() = session.files

    /** A new document called [name] in [folder], at position [at] of the folder's order. */
    suspend fun createDocument(
        folder: ProjectPath,
        name: String,
        at: Int,
    ): Outcome = create(folder, name, at, isFolder = false)

    /** A new folder called [name] in [folder], at position [at] of the folder's order. */
    suspend fun createFolder(
        folder: ProjectPath,
        name: String,
        at: Int,
    ): Outcome = create(folder, name, at, isFolder = true)

    /** [path] renamed [name], in its place, with everything that follows it. */
    suspend fun rename(
        path: ProjectPath,
        name: String,
    ): Outcome =
        session.change { view ->
            val node = view.present(path) ?: return@change Change(Outcome.Failed("$path is not there"))
            if (view.isTaken(path.parent, name)) return@change Change(Outcome.Taken)

            val to = path.parent.child(name)
            val steps =
                listOfNotNull(view.rewrite(view.manifest.file.moved(path, to))) +
                    Renaming(files.editing, node.ref!!, path.name, name) +
                    session.sidecar.carrying(path to to, node is ProjectNode.Folder)
            Change(Carry(steps).run(), moves = listOf(path to to)).onlyIfDone()
        }

    /** [path] moved into [folder], at the end of its order, with everything that follows it. */
    suspend fun move(
        path: ProjectPath,
        folder: ProjectPath,
    ): Outcome =
        session.change { view ->
            val node = view.present(path) ?: return@change Change(Outcome.Failed("$path is not there"))
            val from = view.project.root.folderAt(path.parent)
            val into = view.project.root.folderAt(folder)
            if (from == null || into == null ||
                folder.isWithin(path)
            ) {
                return@change Change(Outcome.Failed("$path cannot go into $folder"))
            }
            if (view.isTaken(folder, path.name)) return@change Change(Outcome.Taken)

            val to = folder.child(path.name)
            val steps =
                listOfNotNull(view.rewrite(view.manifest.file.moved(path, to))) +
                    Moving(files.editing, node.ref!!, from.ref, into.ref) +
                    session.sidecar.carrying(path to to, node is ProjectNode.Folder)
            Change(Carry(steps).run(), moves = listOf(path to to)).onlyIfDone()
        }

    /**
     * [folder]'s children in the order [names] gives. In `manifest` mode that is `project.toml` and
     * nothing else; in `prefix` mode the files are renumbered, and `project.toml` follows the new
     * names if the folder has an order there. Placing an *Unsorted* item is a reorder like any other.
     */
    suspend fun reorder(
        folder: ProjectPath,
        names: List<String>,
    ): Outcome =
        session.change { view ->
            val dir = view.project.root.folderAt(folder) ?: return@change Change(Outcome.Failed("$folder is not there"))
            if (view.manifest.file.project.orderMode == OrderMode.Manifest) {
                return@change Change(
                    Carry(listOfNotNull(view.rewrite(view.manifest.file.reordered(folder, names)))).run(),
                )
            }

            val entries = names.mapNotNull { name -> dir.children.firstOrNull { it.name == name } }
            val reorder = PrefixReorder(files.editing)
            val renames = reorder.planned(entries)
            val steps =
                listOfNotNull(view.rewrite(view.manifest.file.renamed(folder, renames))) +
                    Renumbering(reorder, entries, files, dir.ref) +
                    renames.flatMap { (old, new) ->
                        session.sidecar.carrying(
                            folder.child(old) to folder.child(new),
                            dir.children.first {
                                it.name ==
                                    old
                            } is ProjectNode.Folder,
                        )
                    }
            Change(
                Carry(steps).run(),
                moves =
                    renames.map { (old, new) ->
                        folder.child(old) to folder.child(new)
                    },
            ).onlyIfDone()
        }

    /** A missing name taken out of its folder's order: the only way one leaves it. */
    suspend fun removeFromOrder(path: ProjectPath): Outcome =
        session.change { view ->
            val steps = listOfNotNull(view.rewrite(view.manifest.file.withoutEntry(path)))
            Change(Carry(steps).run(), forgotten = setOf(path)).onlyIfDone()
        }

    private suspend fun create(
        folder: ProjectPath,
        name: String,
        at: Int,
        isFolder: Boolean,
    ): Outcome {
        val made =
            session.change { view ->
                val dir =
                    view.project.root.folderAt(folder) ?: return@change Change(Outcome.Failed("$folder is not there"))
                if (view.isTaken(folder, name)) return@change Change(Outcome.Taken)

                val placed =
                    view.manifest.file.inserted(folder, name, at).takeIf {
                        view.manifest.file.project.orderMode ==
                            OrderMode.Manifest
                    }
                val steps =
                    listOfNotNull(placed?.let { view.rewrite(it) }) + Creating(files.editing, dir.ref, name, isFolder)
                Change(Carry(steps).run())
            }
        // In `prefix` mode a new item's place is its number, which the rest of the folder makes room for.
        val renumber =
            session.view.value?.takeIf {
                made == Outcome.Done &&
                    it.manifest.file.project.orderMode == OrderMode.Prefix
            }
        return renumber?.let { view ->
            val names =
                view.project.root
                    .folderAt(folder)
                    ?.children
                    .orEmpty()
                    .map { it.name } - name
            reorder(folder, names.take(at) + name + names.drop(at))
        } ?: made
    }

    private fun ProjectView.rewrite(after: ProjectFile): Step? = session.rewrite(this, after)
}

/**
 * `project.toml` rewritten to [after], or nothing to do if that is what it already says -- so a
 * rename in a folder with no order never makes a sidecar.
 */
internal fun ProjectSession.rewrite(
    view: ProjectView,
    after: ProjectFile,
): Step? =
    after
        .takeIf {
            it != view.manifest.file
        }?.let { ManifestRewrite(manifests, location.ref, view.manifest, it) }

/** The node at [path] if it is there on disk, not only remembered. */
internal fun ProjectView.present(path: ProjectPath): ProjectNode? = project.root.nodeAt(path)?.takeIf { it.ref != null }

/** Whether [folder] already has something called [name] on disk. */
internal fun ProjectView.isTaken(
    folder: ProjectPath,
    name: String,
): Boolean =
    project.root
        .folderAt(folder)
        ?.children
        .orEmpty()
        .any { it.ref != null && it.name == name }

/** Moves that did not happen are not announced. */
internal fun Change.onlyIfDone(): Change =
    if (outcome ==
        Outcome.Done
    ) {
        this
    } else {
        copy(moves = emptyList(), forgotten = emptySet())
    }

/** A `prefix` reorder as a [Carry] step: renumbered, or every name put back. */
internal class Renumbering(
    private val reorder: PrefixReorder,
    private val entries: List<ProjectNode>,
    private val files: ProjectFiles,
    private val folder: DocumentRef,
) : Step {
    private var renames: Map<String, String> = emptyMap()

    override suspend fun run(): Outcome =
        when (val outcome = reorder.reorder(entries)) {
            is PrefixOutcome.Reordered -> {
                Outcome.Done.also { renames = outcome.renames }
            }

            is PrefixOutcome.NotReordered -> {
                Outcome.Failed(
                    "Renumbering stopped at ${outcome.reason}; stranded ${outcome.stranded}",
                )
            }
        }

    /**
     * Every name put back, through a temporary one first: putting back a swap directly would ask
     * for a name the other file still holds. Found by a fresh listing, since a reference handed back
     * by a rename is not the one it was given.
     */
    override suspend fun undo() {
        val named =
            files.listing
                .entriesOf(folder)
                .orEmpty()
                .associate { it.name to it.ref }
        val parked =
            renames.mapNotNull { (old, new) ->
                named[new]?.let { ref ->
                    files.editing.rename(ref, PARKED + old) to
                        old
                }
            }
        parked.forEach { (outcome, old) -> (outcome as? FolderOutcome.Done)?.let { files.editing.rename(it.ref, old) } }
    }

    private companion object {
        const val PARKED = ".drafts-undo-"
    }
}
