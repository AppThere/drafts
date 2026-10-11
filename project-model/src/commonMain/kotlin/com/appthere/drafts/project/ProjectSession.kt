package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.FolderEditing
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.files.epochMillis
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a project is and what to call it. */
data class ProjectLocation(
    val ref: DocumentRef,
    val name: String,
)

/** What reads and changes the writer's folder: the three platform capabilities a project needs. */
class ProjectFiles(
    val listing: FolderListing,
    val editing: FolderEditing,
    val store: DocumentStore,
)

/** The binder's picture of a project: the ordered tree with what is missing in it, and what is pending. */
data class ProjectView(
    val project: Project,
    val manifest: ManifestState,
    val pending: PendingState,
    /** False while the walk is still reading folders. */
    val complete: Boolean,
)

/** [from] is now [to], at [ref]: for whatever follows a file -- above all, a window showing it. */
data class Moved(
    val from: ProjectPath,
    val to: ProjectPath,
    val ref: DocumentRef,
)

/**
 * One open project, and the one way into it (`projects-core` design D1).
 *
 * Every reconcile and every change the app makes runs under one lock, and a change brings the index
 * up to date before it lets go. A watch event that arrives during or after the app's own change
 * therefore finds the index already agreeing with the folder, and nothing the app did comes back as
 * new, missing or offered -- solved by ordering rather than by trying to recognise our own events.
 * It also means no reconcile ever sees a `prefix` reorder half done.
 *
 * The cost is that a change made outside the app in the moment the app is making its own is taken
 * as the app's own. That window is the length of one binder operation.
 */
class ProjectSession(
    val location: ProjectLocation,
    internal val files: ProjectFiles,
    private val states: ProjectStateStore,
    internal val now: () -> Long = ::epochMillis,
) {
    private val mutex = Mutex()
    private val walk = ProjectWalk(files.listing)
    private val reconciler = Reconciler(files.store)
    internal val manifests = ProjectManifest(files.listing, files.editing, files.store)
    internal val sidecar = Sidecar(location.ref, files.listing, files.editing)

    private var kept: KeptState? = null
    private val state = MutableStateFlow<ProjectView?>(null)
    private val moves = MutableSharedFlow<Moved>(extraBufferCapacity = MOVES_BUFFER)

    /** The project as the binder shows it; null until the first folder has been read. */
    val view: StateFlow<ProjectView?> = state.asStateFlow()

    /** Files the app has renamed or moved, by their old and new paths. */
    val moved: SharedFlow<Moved> = moves.asSharedFlow()

    /**
     * Looks at the folder again (`projects/reconciliation`). [full] lists every folder, as on
     * opening; otherwise only folders that say they changed, as on focus or a change notification.
     */
    suspend fun reconcile(full: Boolean = false) {
        mutex.withLock {
            val before = keptState()
            val walked = walked(before.folders, full) ?: return
            val manifest = manifests.read(location.ref)
            val found =
                reconciler.reconcile(
                    walked.project,
                    manifest.file,
                    before.index,
                    now(),
                    unseen =
                        before.pending.new
                            .map(::ProjectPath)
                            .toSet(),
                    declines = before.pending.declines,
                )
            keep(KeptState(found.index, before.pending.merged(found), walked.cache), walked.project, manifest)
        }
    }

    /**
     * Runs a change the app makes, under the lock, and then takes the folder as it now is for the
     * index without calling anything in it new or missing. [change] answers with what it did and
     * which paths it moved; the pending marks follow those, and [moved] announces them.
     */
    internal suspend fun change(change: suspend (ProjectView) -> Change): Outcome =
        mutex.withLock {
            val before = keptState()
            val current = state.value ?: return Outcome.Failed("${location.name} has not been read")
            val made = change(current)
            if (made.outcome == Outcome.Done || made.moves.isNotEmpty()) absorb(before, made)
            made.outcome
        }

    /** Changes to what is pending only -- a mark dismissed, an offer declined -- which touch no file. */
    internal suspend fun answer(answer: (PendingState, ProjectIndex) -> PendingState) {
        mutex.withLock {
            val before = keptState()
            val view = state.value ?: return
            val pending = answer(before.pending, before.index ?: ProjectIndex())
            keep(before.copy(pending = pending), view.project, view.manifest)
        }
    }

    private suspend fun absorb(
        before: KeptState,
        made: Change,
    ) {
        val walked = walked(before.folders, full = false) ?: return
        val manifest = manifests.read(location.ref)
        val looked = reconciler.reconcile(walked.project, manifest.file, before.index, now())
        val here =
            looked.index.files
                .map { it.path }
                .toSet()
        // The app's own changes make nothing missing: what it moved or put in the trash is where it put it.
        val index =
            looked.index.copy(
                missing =
                    before.index?.missing.orEmpty().filter { gone ->
                        gone.path !in here &&
                            made.forgotten.none { ProjectPath(gone.path).isWithin(it) }
                    },
            )
        val pending =
            made.moves
                .fold(made.answered(before.pending)) { marks, (from, to) -> marks.moved(from, to) }
                .let { it.copy(new = it.new.filter { path -> path in here }.toSet()) }

        keep(KeptState(index, pending, walked.cache), walked.project, manifest)
        announce(made.moves, walked.project)
    }

    private suspend fun walked(
        cache: FolderCache,
        full: Boolean,
    ): Walked? {
        var last: Walked? = null
        walk.walking(location.ref, location.name, cache, full).collect { step ->
            last = step
            if (!step.complete) {
                publish(
                    step.project,
                    state.value?.manifest ?: ManifestState.Absent,
                    keptState(),
                    complete = false,
                )
            }
        }
        return last
    }

    private suspend fun keep(
        kept: KeptState,
        project: Project,
        manifest: ManifestState,
    ) {
        this.kept = kept
        kept.index?.let { states.write(location.ref, kept.pending, it) }
        states.writeFolders(location.ref, kept.folders)
        publish(project, manifest, kept, complete = true)
    }

    private fun publish(
        project: Project,
        manifest: ManifestState,
        kept: KeptState,
        complete: Boolean,
    ) {
        val missing =
            kept.index
                ?.missing
                .orEmpty()
                .map { ProjectPath(it.path) }
        state.value = ProjectView(project.withMissing(missing).ordered(manifest.file), manifest, kept.pending, complete)
    }

    private fun announce(
        moves: List<Pair<ProjectPath, ProjectPath>>,
        project: Project,
    ) {
        moves
            .flatMap { (from, to) ->
                project.root.nodeAt(to)?.let(::nodesUnder).orEmpty().map { node ->
                    Triple(from, to, node)
                }
            }.forEach { (from, to, node) -> moved(from, to, node) }
    }

    private fun moved(
        from: ProjectPath,
        to: ProjectPath,
        node: ProjectNode,
    ) {
        val ref = node.ref ?: return
        moves.tryEmit(Moved(ProjectPath(from.value + node.path.value.removePrefix(to.value)), node.path, ref))
    }

    internal suspend fun keptState(): KeptState = kept ?: states.read(location.ref).also { kept = it }

    private companion object {
        const val MOVES_BUFFER = 64
    }
}

/**
 * What a change did: its [outcome], the paths it [moves] from one place to another, the missing
 * paths it [forgotten] for good (a folder's forgets everything in it), and how it [answered] what
 * was pending.
 */
internal data class Change(
    val outcome: Outcome,
    val moves: List<Pair<ProjectPath, ProjectPath>> = emptyList(),
    val forgotten: Set<ProjectPath> = emptySet(),
    val answered: (PendingState) -> PendingState = { it },
)

/** Every node at or under [node]: itself, and everything inside it if it is a folder. */
private fun nodesUnder(node: ProjectNode): List<ProjectNode> =
    if (node is ProjectNode.Folder) listOf(node) + node.children.flatMap(::nodesUnder) else listOf(node)

/**
 * The open projects of this process, one session each (`projects-core` design D1): a second window
 * on a project, or a document window opened from it, shares the project's session rather than
 * starting a rival one.
 */
class ProjectSessions(
    private val open: (ProjectLocation) -> ProjectSession,
) {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, ProjectSession>()

    /** The session for the project at [location], started if it is not open. */
    suspend fun sessionFor(location: ProjectLocation): ProjectSession =
        mutex.withLock { sessions.getOrPut(projectKey(location.ref)) { open(location) } }

    /** Forgets the session for the project at [ref], once nothing shows it. */
    suspend fun close(ref: DocumentRef) {
        mutex.withLock { sessions.remove(projectKey(ref)) }
    }
}
