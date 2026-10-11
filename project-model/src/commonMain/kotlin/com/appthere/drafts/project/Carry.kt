package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderEditing
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.files.FolderOutcome

/**
 * Everything that follows a file when it moves, done all together or not at all (`projects-core`
 * design D6): its place in the order, its sidecar metadata, its snapshots, and the file itself.
 *
 * The steps run in the order given and, if one fails, the ones already done are undone, latest
 * first. Callers put `project.toml` first, so that a project changed underneath -- a pull, a sync --
 * stops the change before any file has moved, and snapshots last, since they are the one thing here
 * that cannot be made again.
 */
internal class Carry(
    private val steps: List<Step>,
) {
    suspend fun run(): Outcome {
        val done = mutableListOf<Step>()
        val failure =
            steps.firstNotNullOfOrNull { step ->
                step.run().takeIf { it != Outcome.Done }.also { if (it == null) done += step }
            }
        failure?.let { done.asReversed().forEach { step -> step.undo() } }
        return failure ?: Outcome.Done
    }
}

/** One part of a [Carry]. */
internal interface Step {
    suspend fun run(): Outcome

    /** Puts back what [run] did. Called only after [run] answered [Outcome.Done]. */
    suspend fun undo()
}

/** What a change to the project did, in terms the binder can put to the writer. */
sealed interface Outcome {
    /** Done, all of it. */
    data object Done : Outcome

    /** A name already in use where the change would put something. Nothing changed. */
    data object Taken : Outcome

    /** `project.toml` changed since it was read. Nothing changed; read the project again. */
    data object Conflict : Outcome

    /** `project.toml` is not one this version may write: a later schema, or not understood. */
    data object NotWritable : Outcome

    /** A located file has notes or versions of its own, which a relink would have to merge. */
    data object HasHistory : Outcome

    /** The place chosen is already inside a project, and projects do not nest (`projects.md` 13.2). */
    data object InsideProject : Outcome

    /** It could not be done; [detail] is for the log, not the writer. Nothing changed. */
    data class Failed(
        val detail: String,
    ) : Outcome
}

/** A file or folder renamed in place, and named back on undo. */
internal class Renaming(
    private val editing: FolderEditing,
    private val ref: DocumentRef,
    private val from: String,
    private val to: String,
) : Step {
    private var renamed: DocumentRef? = null

    override suspend fun run(): Outcome = editing.rename(ref, to).asOutcome { renamed = it }

    override suspend fun undo() {
        renamed?.let { editing.rename(it, from) }
    }
}

/** A file or folder moved into another folder, and moved back on undo. */
internal class Moving(
    private val editing: FolderEditing,
    private val ref: DocumentRef,
    private val from: DocumentRef,
    private val to: DocumentRef,
) : Step {
    private var moved: DocumentRef? = null

    override suspend fun run(): Outcome = editing.move(ref, from, to).asOutcome { moved = it }

    override suspend fun undo() {
        moved?.let { editing.move(it, to, from) }
    }
}

/** A new empty file or folder, removed on undo. */
internal class Creating(
    private val editing: FolderEditing,
    private val folder: DocumentRef,
    private val name: String,
    private val isFolder: Boolean,
) : Step {
    private var made: DocumentRef? = null

    override suspend fun run(): Outcome =
        (if (isFolder) editing.createFolder(folder, name) else editing.createFile(folder, name)).asOutcome { made = it }

    override suspend fun undo() {
        made?.let { editing.remove(it) }
    }
}

/** `project.toml` rewritten from [before] to [after], and written back on undo. */
internal class ManifestRewrite(
    private val manifests: ProjectManifest,
    private val root: DocumentRef,
    private val before: ManifestState,
    private val after: ProjectFile,
) : Step {
    private var written: ManifestState.Read? = null

    override suspend fun run(): Outcome =
        when (val write = manifests.write(root, after, before)) {
            is ManifestWrite.Written -> Outcome.Done.also { written = write.now }
            ManifestWrite.Conflict -> Outcome.Conflict
            is ManifestWrite.Refused -> Outcome.NotWritable
            is ManifestWrite.Failed -> Outcome.Failed("project.toml: ${write.reason}")
        }

    override suspend fun undo() {
        val now = written ?: return
        manifests.write(root, before.file, now)
    }
}

/**
 * What the sidecar keeps for [from] -- `.drafts/meta/<path>.toml` and `.drafts/snapshots/<path>/`
 * for a file, the folders of those names for a folder -- moved to where [to] keeps it. Nothing to
 * move is not a failure: most files have neither, until Phase 9 writes them.
 */
internal class SidecarCarrying(
    private val sidecar: Sidecar,
    private val kind: Sidecar.Kind,
    private val move: Pair<ProjectPath, ProjectPath>,
    private val isFolder: Boolean,
) : Step {
    private var carried = false

    override suspend fun run(): Outcome {
        val (from, to) = move
        val outcome = sidecar.carry(kind, from, to, isFolder)
        carried = outcome == Outcome.Done && sidecar.find(kind, to, isFolder) != null
        return outcome
    }

    override suspend fun undo() {
        if (carried) sidecar.carry(kind, move.second, move.first, isFolder)
    }
}

/** Both of what the sidecar keeps for [move]'s source carried to its destination: metadata, then snapshots. */
internal fun Sidecar.carrying(
    move: Pair<ProjectPath, ProjectPath>,
    isFolder: Boolean,
): List<Step> =
    listOf(
        SidecarCarrying(this, Sidecar.Kind.Meta, move, isFolder),
        SidecarCarrying(this, Sidecar.Kind.Snapshots, move, isFolder),
    )

/** A project's `.drafts/` as far as moving what it keeps for a file is concerned. */
internal class Sidecar(
    private val root: DocumentRef,
    internal val listing: FolderListing,
    internal val editing: FolderEditing,
) {
    /**
     * What the sidecar keeps per file: metadata, as `<name>.toml`, and snapshots, in `<name>/`. For
     * a folder, both are folders called `<name>`, mirroring the tree.
     */
    enum class Kind(
        val folder: String,
        val suffix: String,
    ) {
        Meta("meta", ".toml"),
        Snapshots("snapshots", ""),
    }

    /** Where [kind] keeps something for [path], if it does. */
    suspend fun find(
        kind: Kind,
        path: ProjectPath,
        isFolder: Boolean = false,
    ): DocumentRef? {
        val folder = folderOf(kind, path.parent, create = false) ?: return null
        return listing.entriesOf(folder)?.firstOrNull { it.name == nameOf(kind, path, isFolder) }?.ref
    }

    /** Whether the file at [path] has history of its own here (`projects/reconciliation`, *Locate file…*). */
    suspend fun hasHistory(path: ProjectPath): Boolean = Kind.entries.any { find(it, path) != null }

    suspend fun carry(
        kind: Kind,
        from: ProjectPath,
        to: ProjectPath,
        isFolder: Boolean,
    ): Outcome {
        val source = find(kind, from, isFolder)
        val sourceFolder = folderOf(kind, from.parent)
        if (source == null || sourceFolder == null) return Outcome.Done

        val moved =
            if (from.parent ==
                to.parent
            ) {
                source
            } else {
                movedInto(source, sourceFolder, folderOf(kind, to.parent, create = true))
            }
        return when {
            moved == null -> Outcome.Failed("$kind of $from could not go to $to")
            from.name == to.name -> Outcome.Done
            else -> editing.rename(moved, nameOf(kind, to, isFolder)).asOutcome { }
        }
    }

    private suspend fun movedInto(
        source: DocumentRef,
        from: DocumentRef,
        to: DocumentRef?,
    ): DocumentRef? = to?.let { (editing.move(source, from, it) as? FolderOutcome.Done)?.ref }

    private fun nameOf(
        kind: Kind,
        path: ProjectPath,
        isFolder: Boolean,
    ): String = if (isFolder) path.name else path.name + kind.suffix

    /** `.drafts/<kind>/<folder>`, made on the way if [create]. */
    internal suspend fun folderOf(
        kind: Kind,
        folder: ProjectPath,
        create: Boolean = false,
    ): DocumentRef? {
        val names = listOf(SIDECAR_FOLDER, kind.folder) + folder.value.split('/').filter { it.isNotEmpty() }
        return names.fold(root as DocumentRef?) { at, name -> at?.let { childFolder(it, name, create) } }
    }

    private suspend fun childFolder(
        parent: DocumentRef,
        name: String,
        create: Boolean,
    ): DocumentRef? =
        listing.entriesOf(parent)?.firstOrNull { it.isFolder && it.name == name }?.ref
            ?: if (create) (editing.createFolder(parent, name) as? FolderOutcome.Done)?.ref else null

    private companion object {
        const val SIDECAR_FOLDER = ".drafts"
    }
}

private inline fun FolderOutcome.asOutcome(done: (DocumentRef) -> Unit): Outcome =
    when (this) {
        is FolderOutcome.Done -> Outcome.Done.also { done(ref) }
        FolderOutcome.Taken -> Outcome.Taken
        is FolderOutcome.Failed -> Outcome.Failed(detail)
    }
