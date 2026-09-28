package com.appthere.drafts.app

import com.appthere.drafts.platform.files.CaretRecord
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotSchedule
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.SnapshotTrigger
import com.appthere.drafts.platform.files.epochMillis

/**
 * 8.1's autosave, for one open document.
 *
 * "**Autosave never touches the user's file.** It writes a snapshot to app-private storage." Every
 * write here goes to [SnapshotStore], which cannot reach the reader's file: there is no digest to
 * check because there is nothing to protect, which is exactly why this can run as often as 8.1 asks
 * without ever needing the reader's permission.
 *
 * Deliberately not a composable and deliberately without a clock. The caller says what time it is
 * and when something happened, so every rule here can be tested at an explicit instant instead of
 * by waiting thirty seconds.
 */
class SnapshotKeeper(
    private val document: OpenDocument,
    private val snapshots: SnapshotStore,
    private val identity: SessionIdentity,
    private val schedule: SnapshotSchedule = SnapshotSchedule(),
) {
    /** Where the document had got to when it was last captured. Null until something is written. */
    var lastTrigger: SnapshotTrigger? = null
        private set

    /** True when the editor has moved on from the last snapshot. */
    fun hasUnsavedEdits(): Boolean = schedule.hasUnsavedEdits

    /** Tells the schedule an edit happened. Call it when the editor's revision changes. */
    fun edited(now: Long) {
        schedule.edited(now)
    }

    /** When the caller should next look, so it can sleep rather than poll. Null when nothing is pending. */
    fun nextDueAt(): Long? = schedule.nextDueAt()

    /** 8.1's two timed rules. Returns the trigger that fired, or null if none was due. */
    suspend fun snapshotIfDue(
        now: Long,
        scrollOffset: Int,
    ): SnapshotTrigger? {
        val due = schedule.dueAt(now) ?: return null
        return if (capture(due, scrollOffset)) due else null
    }

    /**
     * 8.1's three event triggers: focus lost, window closing, navigated away.
     *
     * Each is a moment at which the reader may not get another one, so there is no condition to
     * evaluate beyond whether anything has changed. Writing an identical snapshot on every blur
     * would be wasted I/O on a path that runs whenever someone alt-tabs.
     */
    suspend fun snapshotOn(
        trigger: SnapshotTrigger,
        scrollOffset: Int,
    ): Boolean = schedule.hasUnsavedEdits && capture(trigger, scrollOffset)

    /**
     * 8.3's retention clock, started by a successful save.
     *
     * Called after the bytes are in the reader's file, not before. A stamp written ahead of a write
     * that then failed would make the snapshot prunable while it was still the only copy of the
     * work.
     */
    suspend fun noteSaved(now: Long = epochMillis()): Boolean = snapshots.markSaved(identity.documentId, now)

    /**
     * 8.3's "[ Discard ]", which is the only way a snapshot is ever thrown away deliberately.
     *
     * "Never auto-discard a snapshot" leaves exactly one door open: the reader saying so. The
     * schedule is cleared as well, or the next idle pause would write the snapshot straight back.
     */
    suspend fun discard(): Boolean {
        schedule.snapshotted()
        return snapshots.discard(identity.documentId)
    }

    private suspend fun capture(
        trigger: SnapshotTrigger,
        scrollOffset: Int,
    ): Boolean {
        val written = snapshots.write(recordOf(scrollOffset), document.editor.text)

        if (written) {
            // Only on success. A failed snapshot that cleared the schedule would leave the document
            // looking captured while nothing had been written, and the next trigger would not fire.
            schedule.snapshotted()
            lastTrigger = trigger
        }
        return written
    }

    /**
     * The caret in 7.3's terms: a block *index* and an offset within it.
     *
     * The editor holds a `BlockId`, which is meaningless after a restart -- ids are handed out per
     * session. An index survives, which is why 7.3 stores one.
     */
    private fun recordOf(scrollOffset: Int): SessionRecord {
        val caret = document.editor.caret
        val index = caret?.let { current -> document.editor.blocks.indexOfFirst { it.id == current.block } } ?: -1

        return SessionRecord(
            documentId = identity.documentId,
            uri = identity.uri,
            displayName = identity.displayName,
            kind = identity.kind,
            caret = CaretRecord(blockIndex = index, offset = caret?.offset ?: 0),
            scrollOffset = scrollOffset,
            baseDigest =
                document.lifecycle.base.digest
                    .toString(),
            accessToken = identity.accessToken,
            snapshotPath = snapshots.snapshotOf(identity.documentId).token,
        )
    }
}
