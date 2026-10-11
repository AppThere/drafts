package com.appthere.drafts.platform.files

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [FolderWatching] for a folder granted through the Storage Access Framework (`projects-core`
 * design D8).
 *
 * A provider announces a change on the children URI of the folder that changed, and that folder's
 * document id is the provider's own -- nothing here can tell from it whether it is inside the
 * project. So the watch is on everything the provider announces, under both its document and its
 * tree URIs, and [batched]: a change elsewhere in the same provider costs a reconcile that finds
 * nothing, which the folder cache makes cheap.
 *
 * **Not a guarantee.** Providers announce inconsistently -- Android's own storage provider only
 * while someone holds a listing open, some cloud providers never -- and the system may refuse to
 * watch a provider at all. The guarantee is the reconcile on resume; this only makes changes show
 * sooner where the provider says something.
 */
class SafFolderWatching(
    private val context: Context,
    private val quiet: Long = QUIET_MILLIS,
) : FolderWatching {
    override fun changes(root: DocumentRef): Flow<FolderChange> =
        callbackFlow {
            val resolver = context.contentResolver
            val observer =
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(FolderChange.Changed)
                    }
                }
            val authority = Uri.parse(root.token).authority
            ANNOUNCED.forEach { path ->
                val uri =
                    Uri
                        .Builder()
                        .scheme(
                            ContentResolver.SCHEME_CONTENT,
                        ).authority(authority)
                        .appendPath(path)
                        .build()
                // A provider the system will not let us watch is one the resume reconcile covers.
                runCatching { resolver.registerContentObserver(uri, true, observer) }
            }
            awaitClose { resolver.unregisterContentObserver(observer) }
        }.batched(quiet)

    private companion object {
        const val QUIET_MILLIS = 300L

        /** `content://<authority>/document/...` and `content://<authority>/tree/...`. */
        val ANNOUNCED = listOf("document", "tree")
    }
}
