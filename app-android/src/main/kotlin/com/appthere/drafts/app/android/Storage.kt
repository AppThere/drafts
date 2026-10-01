package com.appthere.drafts.app.android

import android.content.Context
import com.appthere.drafts.app.SettingsStore
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.androidSessionRoot
import com.appthere.drafts.platform.files.androidSettingsRoot
import com.appthere.drafts.platform.windows.SessionList

/**
 * Everything this application keeps for itself, on Android.
 *
 * 8.1's snapshots, 7.3's session records and 5.5's settings all live in app-private storage and are
 * reached by path, so they go through [PathDocumentStore] rather than the Storage Access Framework.
 * The reader's own documents do not: those are `content://` URIs and are reached through
 * `SafDocumentStore`, which needs the Activity that was granted them.
 *
 * Built per Activity rather than held in the Application object. Each is a handle to a directory
 * with no mutable state of its own, and a document task that is killed should take its own handles
 * with it rather than leaving them attached to a process that outlives it.
 */
internal class Storage(
    context: Context,
) {
    val files = PathDocumentStore()
    val snapshots = SnapshotStore(files, androidSessionRoot(context))
    val sessions = SessionList(snapshots)
    val settings = SettingsStore(files, androidSettingsRoot(context))
}
