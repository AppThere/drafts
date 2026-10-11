package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.sha256
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * What this device keeps about a project, in the application's own storage
 * (`projects-core` design D3): `<root>/<project key>/index.json`, what the folder looked like;
 * `pending.json`, what the writer has not answered yet; and `folders.json`, what the last walk listed.
 *
 * Not in the project's `.drafts/`. Both are this device's, and a sync client would hand them to
 * every other device, where they would say a file had been seen that had not. Kept here, they need
 * no sidecar either: a folder that is only browsed is reconciled in full and still never written to
 * (`projects.md` 13.1).
 *
 * [root] is supplied by the platform entry point, as for `SnapshotStore`, and so is a store that
 * writes atomically: losing the index costs a baseline, but a torn one would cost a wrong one.
 */
class ProjectStateStore(
    private val store: DocumentStore,
    private val root: String,
) {
    init {
        require(store.writesAtomically) {
            "Project state needs a store that writes atomically; ${store::class.simpleName} does not"
        }
    }

    /** What was kept for the project at [project]: nothing, on a first open or after a move. */
    suspend fun read(project: DocumentRef): KeptState =
        KeptState(
            index = decoded(fileOf(project, INDEX), ProjectIndex.serializer()),
            pending = decoded(fileOf(project, PENDING), PendingState.serializer()) ?: PendingState(),
            folders = decoded(fileOf(project, FOLDERS), FolderCache.serializer()) ?: FolderCache(),
        )

    /**
     * Keeps what the last walk listed. Separate from [write], and in no order with it: a lost or
     * stale cache costs the next walk some listings, and nothing else.
     */
    suspend fun writeFolders(
        project: DocumentRef,
        folders: FolderCache,
    ): Boolean = written(fileOf(project, FOLDERS), format.encodeToString(FolderCache.serializer(), folders))

    /**
     * Keeps both, pending state first. If the second write never happens, the next reconcile
     * compares against the older index, finds the new files new again, and marks them again. The
     * other order could keep an index that had seen a file whose *New* mark was never kept, and the
     * mark would be lost for good. True if both were kept.
     */
    suspend fun write(
        project: DocumentRef,
        pending: PendingState,
        index: ProjectIndex,
    ): Boolean =
        written(fileOf(project, PENDING), format.encodeToString(PendingState.serializer(), pending)) &&
            written(fileOf(project, INDEX), format.encodeToString(ProjectIndex.serializer(), index))

    private suspend fun written(
        ref: DocumentRef,
        text: String,
    ): Boolean = store.writeAtomically(ref, text) is WriteOutcome.Written

    private suspend fun <T> decoded(
        ref: DocumentRef,
        serializer: KSerializer<T>,
    ): T? = runCatching { format.decodeFromString(serializer, store.read(ref).text) }.getOrNull()

    private fun fileOf(
        project: DocumentRef,
        name: String,
    ): DocumentRef = DocumentRef("$root/${projectKey(project)}/$name")

    private companion object {
        const val INDEX = "index.json"
        const val PENDING = "pending.json"
        const val FOLDERS = "folders.json"

        val format = Json { ignoreUnknownKeys = true }
    }
}

/** What [ProjectStateStore.read] found: [index] is null when there is none, and the next reconcile a baseline. */
data class KeptState(
    val index: ProjectIndex?,
    val pending: PendingState,
    val folders: FolderCache = FolderCache(),
)

/**
 * What this device files a project's own state under: its location, digested
 * (`projects/reconciliation`, "if the project's folder moves, the next reconcile there SHALL be a
 * baseline"). Not an id written into `project.toml`, which would need a write to the folder and
 * would be shared by every copy of it.
 */
fun projectKey(project: DocumentRef): String = sha256(project.token.encodeToByteArray()).hex
