package com.appthere.drafts.project

import com.akuleshov7.ktoml.Toml
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderOutcome
import com.appthere.drafts.platform.files.WriteOutcome
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The project's trash (`projects/trash`, `projects.md` 7): "never lose work" extended to deleting.
 *
 * A delete moves the item to `.drafts/trash/<original path>/<UTC time>/`, with its sidecar metadata
 * as `.meta.toml`, its snapshots as `.snapshots/`, and an `.entry.toml` saying where it was -- its
 * folder, its name, and the names either side of it. Those three begin with a full stop because no
 * item the binder shows does, so none can be in the way of the item itself. Neighbours rather than
 * a position, so that a folder reordered while the item was in the trash still has somewhere
 * sensible to put it back.
 *
 * Nothing is removed from storage except by [empty], which the writer asks for and confirms.
 */
class Trash(
    private val session: ProjectSession,
) {
    private val files get() = session.files

    /** [path] into the trash, with its metadata, snapshots and place. */
    suspend fun delete(path: ProjectPath): Outcome =
        session.change { view ->
            val node = view.present(path) ?: return@change Change(Outcome.Failed("$path is not there"))
            val parent =
                view.project.root.folderAt(path.parent) ?: return@change Change(Outcome.Failed("$path has no folder"))
            val at =
                sidecarFolder(listOf(TRASH_FOLDER) + path.segments() + stamp(session.now()), create = true)
                    ?: return@change Change(Outcome.Failed("No room in the trash for $path"))

            val names = parent.children.map { it.name }
            val place = names.indexOf(path.name)
            val entry =
                TrashEntry(
                    folder = path.parent.value,
                    name = path.name,
                    before = names.getOrNull(place - 1),
                    after = names.getOrNull(place + 1),
                    isFolder = node is ProjectNode.Folder,
                    deletedEpochMillis = session.now(),
                )
            val steps =
                listOfNotNull(session.rewrite(view, view.manifest.file.withoutEntry(path))) +
                    Moving(files.editing, node.ref!!, parent.ref, at) +
                    SidecarToTrash(session.sidecar, path, entry.isFolder, at) +
                    EntryWriting(this, at, entry)
            val outcome = Carry(steps).run()
            // A delete that did not happen leaves no empty folder in the trash to say it was tried.
            if (outcome != Outcome.Done && files.listing.entriesOf(at).isNullOrEmpty()) files.editing.remove(at)
            Change(outcome, forgotten = setOf(path)).onlyIfDone()
        }

    /** What is in the trash, newest first. */
    suspend fun items(): List<Trashed> =
        sidecarFolder(listOf(TRASH_FOLDER), create = false)
            ?.let {
                trashed(it)
            }.orEmpty()
            .sortedByDescending { it.entry.deletedEpochMillis }

    /**
     * [item] back where it was, as [name] -- its own, unless the writer gave another because
     * something now has it ([Outcome.Taken], and nothing restored). Its folder is made again if it
     * has gone; it goes back between the same neighbours if they are still there, and at the end if
     * not.
     */
    suspend fun restore(
        item: Trashed,
        name: String = item.entry.name,
    ): Outcome =
        session.change { view ->
            val folder = ProjectPath(item.entry.folder)
            if (view.isTaken(folder, name)) return@change Change(Outcome.Taken)
            val into =
                folderIn(folder.segments(), create = true)
                    ?: return@change Change(Outcome.Failed("$folder could not be made again"))
            val inTrash = files.listing.entriesOf(item.ref).orEmpty()
            val restored =
                inTrash.firstOrNull { it.name == item.entry.name }
                    ?: return@change Change(Outcome.Failed("${item.entry.name} is not in the trash"))

            val path = folder.child(name)
            val steps =
                listOfNotNull(session.rewrite(view, view.manifest.file.restored(item.entry, name))) +
                    listOfNotNull(
                        Renaming(files.editing, restored.ref, item.entry.name, name).takeIf {
                            name !=
                                item.entry.name
                        },
                    ) +
                    MovingByName(files, item.ref, name, into) +
                    SidecarFromTrash(session.sidecar, item, path)
            val outcome = Carry(steps).run()
            if (outcome == Outcome.Done) files.editing.remove(item.ref)
            Change(outcome)
        }

    /** [item] gone for good. Only when the writer asks, and has confirmed. */
    suspend fun empty(item: Trashed): Outcome =
        session.change { _ ->
            when (val removed = files.editing.remove(item.ref)) {
                is FolderOutcome.Done -> Change(Outcome.Done)
                else -> Change(Outcome.Failed("$removed"))
            }
        }

    /** Everything in the trash gone for good. Only when the writer asks, and has confirmed. */
    suspend fun emptyAll(): Outcome {
        val trash = sidecarFolder(listOf(TRASH_FOLDER), create = false) ?: return Outcome.Done
        return session.change { _ ->
            val failed =
                files.listing
                    .entriesOf(trash)
                    .orEmpty()
                    .map {
                        files.editing.remove(it.ref)
                    }.firstOrNull { it !is FolderOutcome.Done }
            Change(if (failed == null) Outcome.Done else Outcome.Failed("$failed"))
        }
    }

    /**
     * Sidecar metadata left behind by files deleted outside the app (`projects/trash`, "Deletion
     * outside the app is not trashed"), which the writer may be offered the removal of: only once
     * the file has been missing for 30 days, or is no longer remembered at all.
     */
    suspend fun orphanedMetadata(): List<ProjectPath> {
        val view = session.view.value
        val meta = sidecarFolder(listOf(META), create = false)
        val recent =
            session
                .keptState()
                .index
                ?.missing
                .orEmpty()
                .map { it.path }
                .toSet()
        return if (view == null || meta == null) {
            emptyList()
        } else {
            metadataUnder(meta, ProjectPath.Root).filter { view.present(it) == null && it.value !in recent }
        }
    }

    /** The metadata of [path] removed, if it is orphaned; its snapshots are kept, whatever happens. */
    suspend fun removeMetadata(path: ProjectPath): Outcome =
        if (path !in orphanedMetadata()) {
            Outcome.Failed("$path is not orphaned")
        } else {
            session.change { _ ->
                val ref = session.sidecar.find(Sidecar.Kind.Meta, path)
                Change(
                    if (ref == null ||
                        files.editing.remove(ref) is FolderOutcome.Done
                    ) {
                        Outcome.Done
                    } else {
                        Outcome.Failed("$path")
                    },
                )
            }
        }

    private suspend fun trashed(folder: DocumentRef): List<Trashed> {
        val entries = files.listing.entriesOf(folder).orEmpty()
        val entry = entries.firstOrNull { it.name == ENTRY }
        if (entry != null) return listOfNotNull(readEntry(entry.ref)?.let { Trashed(folder, it) })
        return entries.filter { it.isFolder }.flatMap { trashed(it.ref) }
    }

    private suspend fun metadataUnder(
        folder: DocumentRef,
        path: ProjectPath,
    ): List<ProjectPath> =
        files.listing.entriesOf(folder).orEmpty().flatMap { entry ->
            when {
                entry.isFolder -> metadataUnder(entry.ref, path.child(entry.name))
                entry.name.endsWith(META_SUFFIX) -> listOf(path.child(entry.name.removeSuffix(META_SUFFIX)))
                else -> emptyList()
            }
        }

    private suspend fun readEntry(ref: DocumentRef): TrashEntry? =
        runCatching { Toml.decodeFromString(TrashEntry.serializer(), files.store.read(ref).text) }.getOrNull()

    internal suspend fun removeEntry(ref: DocumentRef) {
        files.editing.remove(ref)
    }

    internal suspend fun writeEntry(
        at: DocumentRef,
        entry: TrashEntry,
    ): DocumentRef? {
        val made = files.editing.createFile(at, ENTRY) as? FolderOutcome.Done ?: return null
        val written = files.store.writeAtomically(made.ref, Toml.encodeToString(TrashEntry.serializer(), entry))
        return made.ref.takeIf { written is WriteOutcome.Written }
    }

    /** `.drafts/<names...>`, made on the way if [create]. */
    private suspend fun sidecarFolder(
        names: List<String>,
        create: Boolean,
    ): DocumentRef? = folderIn(listOf(SIDECAR_FOLDER) + names, create)

    /** `<project>/<names...>`, made on the way if [create]. */
    private suspend fun folderIn(
        names: List<String>,
        create: Boolean,
    ): DocumentRef? =
        names.fold(session.location.ref as DocumentRef?) { at, name ->
            at?.let { parent ->
                files.listing
                    .entriesOf(parent)
                    ?.firstOrNull { it.isFolder && it.name == name }
                    ?.ref
                    ?: if (create) (files.editing.createFolder(parent, name) as? FolderOutcome.Done)?.ref else null
            }
        }

    internal companion object {
        const val SIDECAR_FOLDER = ".drafts"
        const val TRASH_FOLDER = "trash"
        const val META = "meta"
        const val META_SUFFIX = ".toml"

        // Dot-names: the binder never shows one, so nothing the writer can delete can be called this.
        const val ENTRY = ".entry.toml"
        const val META_IN_TRASH = ".meta.toml"
        const val SNAPSHOTS_IN_TRASH = ".snapshots"
    }
}

/** Where a trashed item was, and when it was deleted: the trash folder's `entry.toml`. */
@Serializable
data class TrashEntry(
    val folder: String,
    val name: String,
    val before: String? = null,
    val after: String? = null,
    val isFolder: Boolean = false,
    val deletedEpochMillis: Long = 0,
)

/** One item in the trash: the folder that holds it ([ref]) and what [entry] says about it. */
data class Trashed(
    val ref: DocumentRef,
    val entry: TrashEntry,
)

/** The project file with a restored item back in its folder's order, between its old neighbours if it can be. */
internal fun ProjectFile.restored(
    entry: TrashEntry,
    name: String,
): ProjectFile {
    val folder = project.order.firstOrNull { it.folder == entry.folder } ?: return this
    val at =
        entry.before
            ?.let { folder.entries.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: entry.after?.let { folder.entries.indexOf(it) }?.takeIf { it >= 0 }
            ?: folder.entries.size
    return inserted(ProjectPath(entry.folder), name, at)
}

/** `entry.toml` written into the trash folder, and removed on undo. */
private class EntryWriting(
    private val trash: Trash,
    private val at: DocumentRef,
    private val entry: TrashEntry,
) : Step {
    private var written: DocumentRef? = null

    override suspend fun run(): Outcome =
        trash.writeEntry(at, entry)?.let { Outcome.Done.also { _ -> written = it } }
            ?: Outcome.Failed("entry.toml could not be written")

    override suspend fun undo() {
        written?.let { trash.removeEntry(it) }
    }
}

/** A trashed item moved out of its trash folder, found there by [name]; moved back on undo. */
private class MovingByName(
    private val files: ProjectFiles,
    private val from: DocumentRef,
    private val name: String,
    private val to: DocumentRef,
) : Step {
    private var moved: DocumentRef? = null

    override suspend fun run(): Outcome {
        val item =
            files.listing.entriesOf(from)?.firstOrNull { it.name == name }
                ?: return Outcome.Failed("$name is not in the trash")
        return when (val outcome = files.editing.move(item.ref, from, to)) {
            is FolderOutcome.Done -> Outcome.Done.also { moved = outcome.ref }
            FolderOutcome.Taken -> Outcome.Taken
            is FolderOutcome.Failed -> Outcome.Failed(outcome.detail)
        }
    }

    override suspend fun undo() {
        moved?.let { files.editing.move(it, to, from) }
    }
}

/**
 * A path's metadata and snapshots moved into its trash folder, under the trash's names for them.
 * Each is named for the trash first, where it is: under its own name it would meet the item it
 * belongs to, which is already there.
 */
private class SidecarToTrash(
    private val sidecar: Sidecar,
    private val path: ProjectPath,
    private val isFolder: Boolean,
    private val at: DocumentRef,
) : Step {
    private val moved = mutableListOf<Carried>()

    override suspend fun run(): Outcome {
        val failed = Sidecar.Kind.entries.firstOrNull { !carried(it) }
        if (failed != null) undo()
        return if (failed == null) Outcome.Done else Outcome.Failed("$failed of $path")
    }

    private suspend fun carried(kind: Sidecar.Kind): Boolean {
        val source = sidecar.find(kind, path, isFolder)
        val folder = sidecar.folderOf(kind, path.parent)
        if (source == null || folder == null) return true

        val named = (sidecar.editing.rename(source, kind.inTrash()) as? FolderOutcome.Done)?.ref
        val inTrash = named?.let { (sidecar.editing.move(it, folder, at) as? FolderOutcome.Done)?.ref }
        if (named != null && inTrash == null) sidecar.editing.rename(named, kind.nameFor(path, isFolder))
        inTrash?.let { moved += Carried(kind, it, folder) }
        return inTrash != null
    }

    override suspend fun undo() {
        moved.asReversed().forEach { (kind, ref, folder) ->
            (sidecar.editing.move(ref, at, folder) as? FolderOutcome.Done)?.let {
                sidecar.editing.rename(it.ref, kind.nameFor(path, isFolder))
            }
        }
        moved.clear()
    }
}

/** A trashed item's metadata and snapshots put back in the sidecar, for its restored [path]. */
private class SidecarFromTrash(
    private val sidecar: Sidecar,
    private val item: Trashed,
    private val path: ProjectPath,
) : Step {
    private val moved = mutableListOf<Carried>()

    override suspend fun run(): Outcome {
        val failed = Sidecar.Kind.entries.firstOrNull { !carried(it) }
        if (failed != null) undo()
        return if (failed == null) Outcome.Done else Outcome.Failed("$failed of $path")
    }

    private suspend fun carried(kind: Sidecar.Kind): Boolean {
        val source = sidecar.listing.entriesOf(item.ref)?.firstOrNull { it.name == kind.inTrash() } ?: return true
        val folder = sidecar.folderOf(kind, path.parent, create = true)
        val ownName = kind.nameFor(path, item.entry.isFolder)
        val renamed = folder?.let { (sidecar.editing.rename(source.ref, ownName) as? FolderOutcome.Done)?.ref }
        val outcome = if (renamed != null && folder != null) sidecar.editing.move(renamed, item.ref, folder) else null
        val back = (outcome as? FolderOutcome.Done)?.ref
        if (renamed != null && back == null) sidecar.editing.rename(renamed, kind.inTrash())
        if (back != null && folder != null) moved += Carried(kind, back, folder)
        return back != null
    }

    override suspend fun undo() {
        moved.asReversed().forEach { (kind, ref, folder) ->
            (sidecar.editing.move(ref, folder, item.ref) as? FolderOutcome.Done)?.let {
                sidecar.editing.rename(it.ref, kind.inTrash())
            }
        }
        moved.clear()
    }
}

/** One part of the sidecar moved: which, where it is now, and the sidecar folder on the other side. */
private data class Carried(
    val kind: Sidecar.Kind,
    val ref: DocumentRef,
    val folder: DocumentRef,
)

private fun Sidecar.Kind.nameFor(
    path: ProjectPath,
    isFolder: Boolean,
): String = if (isFolder) path.name else path.name + suffix

private fun Sidecar.Kind.inTrash(): String =
    if (this ==
        Sidecar.Kind.Meta
    ) {
        Trash.META_IN_TRASH
    } else {
        Trash.SNAPSHOTS_IN_TRASH
    }

private fun ProjectPath.segments(): List<String> = value.split('/').filter { it.isNotEmpty() }

/** [millis] as a UTC time a person can read in a file manager, and that sorts: `2026-10-10T051500Z`. */
internal fun stamp(millis: Long): String =
    Instant
        .fromEpochSeconds(Instant.fromEpochMilliseconds(millis).epochSeconds)
        .toString()
        .replace(":", "")
