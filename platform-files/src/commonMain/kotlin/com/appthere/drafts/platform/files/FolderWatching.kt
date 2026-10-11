package com.appthere.drafts.platform.files

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tells the app that something in a project's folder changed (`projects/reconciliation`: "when the
 * platform reports a change in the project's folder").
 *
 * A signal and nothing more. What changed is reconciliation's to work out from the folder itself:
 * platforms report changes late, out of order, coalesced, or not at all, and a design that trusted
 * the events would inherit every one of those faults (`projects-core` design D8).
 */
interface FolderWatching {
    /** Changes under [root], for as long as the flow is collected. */
    fun changes(root: DocumentRef): Flow<FolderChange>
}

/** What a watcher can say. */
sealed interface FolderChange {
    /** Something under the root changed since the last signal. */
    data object Changed : FolderChange

    /**
     * The platform lost track -- too many changes at once, or a folder it could not follow. Anything
     * may have changed, so nothing remembered about the folders can be trusted for this reconcile.
     */
    data object Lost : FolderChange
}

/**
 * Gathers signals for [quiet] milliseconds after the first and passes on one: [FolderChange.Lost]
 * if any of them was. A save, a `git checkout`, or a folder dropped in from a file manager raises
 * dozens of events, and each would otherwise be a reconcile.
 */
fun Flow<FolderChange>.batched(quiet: Long): Flow<FolderChange> =
    channelFlow {
        val raw = produceIn(this)
        for (first in raw) {
            var batch = first
            // Until the burst has been quiet for [quiet], or the source has ended.
            var next = withTimeoutOrNull(quiet) { raw.receiveCatching() }?.getOrNull()
            while (next != null) {
                if (next == FolderChange.Lost) batch = FolderChange.Lost
                next = withTimeoutOrNull(quiet) { raw.receiveCatching() }?.getOrNull()
            }
            send(batch)
        }
    }
