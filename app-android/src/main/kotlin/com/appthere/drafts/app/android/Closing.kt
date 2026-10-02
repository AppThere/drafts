package com.appthere.drafts.app.android

import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.platform.files.SnapshotTrigger
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Closing one document: `appthere-drafts.md` 8.1's last snapshot, and 7.3's session marked closed.
 *
 * The snapshot goes first because 8.1 calls it "window close, before teardown", and because that
 * same call is where 7.4's "an untitled document that is still empty when closed is discarded"
 * happens. A discarded document has no record left to mark, so [SessionList.closed] finds nothing
 * and says so, which is the right answer rather than an error: the session is gone, which is more
 * closed than closed.
 *
 * The order is not load-bearing, which was worth checking rather than assuming: reversing it leaves
 * every test green, because `SnapshotStore.carriedOver` keeps `closedAt` from the record already on
 * disk. It is written this way round because that is the order the two things happen in.
 *
 * Everything else that was open is untouched. 7.3 restores "every session still open", and closing
 * one document is not closing the application.
 */
internal suspend fun closeDocument(
    sessions: SessionList,
    keeper: SnapshotKeeper?,
    documentId: String,
) {
    keeper?.snapshotOn(SnapshotTrigger.Closing)
    sessions.closed(documentId)
}

/**
 * Where that runs, once the Activity that asked for it is on its way out.
 *
 * It cannot be the Activity's own scope. `lifecycleScope` is cancelled the moment the lifecycle
 * reaches DESTROYED, which is immediately after the work is asked for, so the write would be
 * cancelled before it reached the disk. It cannot be `runBlocking` either: that parks the main
 * thread, and `engineering-conventions.md` 4.1 bans it outside tests for exactly that reason.
 *
 * So a scope belonging to the process. An Android process is not killed the instant its last
 * Activity goes -- it becomes a cached process and lives until something needs the memory -- and
 * the write is a few kilobytes to app-private storage, so in practice it lands. "In practice" is
 * the honest word: a process killed in that window loses the closing snapshot and leaves the
 * session open, so the document reopens next launch with whatever the last idle snapshot held.
 * That is the same failure 8.1's other four triggers already guard against, which is why it is
 * tolerable here and would not be as the only thing keeping someone's words.
 *
 * `Dispatchers.Default` rather than IO: the stores choose their own dispatcher, which is where the
 * choice belongs (4.2), and nothing here blocks.
 */
internal val closingScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
