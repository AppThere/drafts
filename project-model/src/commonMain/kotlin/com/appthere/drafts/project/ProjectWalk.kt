package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderEntry
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.intents.DocumentKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.serialization.Serializable

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
 *
 * **Progressive** (`projects/folder-as-project`): [walking] gives the tree after every folder it
 * lists, the rest [ProjectNode.Folder.loading], so the binder shows the top of a large project over
 * Android's storage framework long before the bottom has been read. **Cached**: a walk that is not
 * [full] asks each folder when it last changed, which is one cheap question, and lists only those
 * whose answer moved, or that have none.
 */
class ProjectWalk(
    private val listing: FolderListing,
) {
    /** The project at [root], called [name], walked in full; null if [root] itself cannot be read. */
    suspend fun open(
        root: DocumentRef,
        name: String,
    ): Project? = walking(root, name).lastOrNull()?.project

    /**
     * The project as it is read, folder by folder, the last emission [Walked.complete]. Nothing is
     * emitted if [root] itself cannot be read. [full] lists every folder whatever [cache] says: on
     * opening, which bounds what a skipped folder can hide to one session.
     */
    fun walking(
        root: DocumentRef,
        name: String,
        cache: FolderCache = FolderCache(),
        full: Boolean = true,
    ): Flow<Walked> =
        flow {
            val listed = mutableMapOf<ProjectPath, Listed>()
            val queue = ArrayDeque(listOf(ProjectPath.Root to root))

            while (queue.isNotEmpty()) {
                val (path, ref) = queue.removeFirst()
                val folder = list(ref, cache.folders[path.value], full)
                if (path == ProjectPath.Root && folder.entries == null) return@flow
                listed[path] = folder
                queue.addAll(foldersToWalk(path, folder))
                emit(snapshot(listed, name, root, complete = queue.isEmpty()))
            }
        }

    /** The folders inside one just listed, to list next -- none, if it is another project's (13.2). */
    private fun foldersToWalk(
        path: ProjectPath,
        folder: Listed,
    ): List<Pair<ProjectPath, DocumentRef>> {
        val entries = folder.entries.orEmpty()
        val nested = path != ProjectPath.Root && entries.any { it.isSidecar() }
        return if (nested) {
            emptyList()
        } else {
            entries.filter { it.isFolder && !it.name.startsWith(HIDDEN) }.map {
                path.child(it.name) to
                    it.ref
            }
        }
    }

    /** The project as far as [listed] goes, and what to remember of it. */
    private fun snapshot(
        listed: Map<ProjectPath, Listed>,
        name: String,
        root: DocumentRef,
        complete: Boolean,
    ) = Walked(
        project =
            Project(
                name = name,
                root = Tree(listed).folder(name, ProjectPath.Root, root),
                hasSidecar =
                    listed
                        .getValue(ProjectPath.Root)
                        .entries
                        .orEmpty()
                        .any { it.isSidecar() },
            ),
        complete = complete,
        cache =
            FolderCache(
                listed
                    .mapNotNull { (at, it) ->
                        it.cached()?.let { cached -> at.value to cached }
                    }.toMap(),
            ),
    )

    /** One folder's entries: remembered, if it says it has not changed since; listed otherwise. */
    private suspend fun list(
        ref: DocumentRef,
        cached: CachedFolder?,
        full: Boolean,
    ): Listed {
        val modified = listing.modifiedOf(ref)
        val remembered = cached?.takeIf { !full && modified != 0L && it.modifiedEpochMillis == modified }

        return remembered?.let { Listed(modified, it.entries.map(CachedEntry::entry)) }
            ?: Listed(modified, listing.entriesOf(ref))
    }

    /** The folders listed so far, as a tree. */
    private class Tree(
        private val listed: Map<ProjectPath, Listed>,
    ) {
        fun folder(
            name: String,
            path: ProjectPath,
            ref: DocumentRef,
        ): ProjectNode.Folder {
            val entries = listed[path]?.entries
            return when {
                path !in listed -> ProjectNode.Folder(name, path, ref, emptyList(), loading = true)

                entries == null -> ProjectNode.Folder(name, path, ref, emptyList(), unreadable = true)

                // A folder with a sidecar of its own is another project's (13.2): shown, not walked.
                path != ProjectPath.Root &&
                    entries.any {
                        it.isSidecar()
                    }
                -> ProjectNode.Folder(name, path, ref, emptyList(), nestedProject = true)

                else -> ProjectNode.Folder(name = name, path = path, ref = ref, children = childrenOf(entries, path))
            }
        }

        private fun childrenOf(
            entries: List<FolderEntry>,
            path: ProjectPath,
        ): List<ProjectNode> =
            entries
                .filterNot { it.name.startsWith(HIDDEN) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { entry -> node(entry, path.child(entry.name)) }

        private fun node(
            entry: FolderEntry,
            path: ProjectPath,
        ): ProjectNode = if (entry.isFolder) folder(entry.name, path, entry.ref) else file(entry, path)

        /** A document if its extension names a kind (`projects.md` 5), and an attachment otherwise. */
        private fun file(
            entry: FolderEntry,
            path: ProjectPath,
        ): ProjectNode =
            DocumentKind.of(entry.name)?.let { kind ->
                ProjectNode.Document(entry.name, path, entry.ref, kind, modifiedEpochMillis = entry.modifiedEpochMillis)
            } ?: ProjectNode.Attachment(entry.name, path, entry.ref, modifiedEpochMillis = entry.modifiedEpochMillis)
    }

    /** A folder as listed: when it last changed (0 if unknown), and what is in it (null if unreadable). */
    private class Listed(
        val modifiedEpochMillis: Long,
        val entries: List<FolderEntry>?,
    ) {
        fun cached(): CachedFolder? =
            entries?.takeIf { modifiedEpochMillis != 0L }?.let { list ->
                CachedFolder(
                    modifiedEpochMillis,
                    list.map { CachedEntry(it.name, it.ref.token, it.isFolder, it.modifiedEpochMillis) },
                )
            }
    }

    private companion object {
        /** `projects.md` 4: the sidecar every project keeps its order and metadata in. */
        const val SIDECAR = ".drafts"

        const val HIDDEN = "."

        fun FolderEntry.isSidecar(): Boolean = isFolder && name == SIDECAR
    }
}

/** The project as far as it has been read, and what to remember of the folders for next time. */
data class Walked(
    val project: Project,
    val complete: Boolean,
    val cache: FolderCache,
)

/** What a walk remembers of each folder it listed, by project path. Kept per device, beside the index. */
@Serializable
data class FolderCache(
    val folders: Map<String, CachedFolder> = emptyMap(),
)

/** One folder as last listed, and when it had last changed then. */
@Serializable
data class CachedFolder(
    val modifiedEpochMillis: Long,
    val entries: List<CachedEntry>,
)

/** One entry of a [CachedFolder]. */
@Serializable
data class CachedEntry(
    val name: String,
    val token: String,
    val isFolder: Boolean,
    val modifiedEpochMillis: Long,
) {
    fun entry(): FolderEntry = FolderEntry(name, DocumentRef(token), isFolder, modifiedEpochMillis)
}
