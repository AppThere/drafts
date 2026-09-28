package com.appthere.drafts.platform.windows

import com.appthere.drafts.platform.files.CaretRecord
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.WindowRecord
import com.appthere.drafts.platform.files.epochMillis
import com.appthere.drafts.platform.files.sha256

/**
 * Which documents are open, across restarts.
 *
 * `appthere-drafts.md` 7.2: "Maintain a `List<DocumentSession>` in the application state and emit
 * one `Window` per entry, each with its own `WindowState` (position, size, placement) persisted."
 * This is the persisted half; the windows are the platform's.
 *
 * There is no index file. The list is the set of session records that are not marked closed, which
 * keeps it in step with itself by construction -- an index is a second thing to update, and the
 * failure of an index that drifts is a document that either reopens after being closed or never
 * comes back at all.
 *
 * Records are written through [SnapshotStore] because that is where 7.3 puts them, beside the
 * snapshot they describe. This type owns *which* documents are open; that one owns the file.
 */
class SessionList(
    private val snapshots: SnapshotStore,
) {
    /**
     * Records a document as open, or brings an already-known one back.
     *
     * Keeps whatever the existing record knew -- the caret, the scroll, the window, the retention
     * stamp. Reopening a document should put the reader back where they were, and a fresh record
     * would throw all of that away on the way in.
     */
    suspend fun opened(identity: SessionIdentity): SessionRecord {
        val existing = snapshots.recordOf(identity.documentId)
        val record =
            existing?.copy(
                uri = identity.uri,
                displayName = identity.displayName,
                accessToken = identity.accessToken,
                closedAt = null,
            ) ?: startedFrom(identity, snapshots.snapshotOf(identity.documentId).token)

        snapshots.putRecord(record)
        return record
    }

    /**
     * Marks a document closed without forgetting it.
     *
     * The snapshot stays: 8.3 keeps it for thirty days after a successful save, and a document
     * closed with unsaved work keeps its snapshot indefinitely. What changes is only whether the
     * next launch reopens a window for it.
     */
    suspend fun closed(
        documentId: String,
        now: Long = epochMillis(),
    ): Boolean {
        val record = snapshots.recordOf(documentId) ?: return false

        return snapshots.putRecord(record.copy(closedAt = now))
    }

    /** Persists a window's geometry, per 7.2's "each with its own `WindowState` ... persisted". */
    suspend fun remember(
        documentId: String,
        window: WindowRecord,
    ): Boolean {
        val record = snapshots.recordOf(documentId) ?: return false

        return snapshots.putRecord(record.copy(window = window))
    }

    /**
     * 7.3's "On launch, restore every session" -- every session still open, that is.
     *
     * Records that cannot be read are skipped rather than failing the launch. One damaged
     * `meta.json` must not cost the reader every other document they had open.
     *
     * Ordered by display name so the windows come back in a stable order rather than whatever
     * order the filesystem happened to list the directories in.
     */
    suspend fun restorable(): List<SessionRecord> =
        snapshots
            .sessions()
            .mapNotNull { snapshots.recordOf(it) }
            .filter { it.closedAt == null }
            .sortedBy { it.displayName }
}

/** A session record for a document nothing has recorded before. */
private fun startedFrom(
    identity: SessionIdentity,
    snapshotPath: String,
) = SessionRecord(
    documentId = identity.documentId,
    uri = identity.uri,
    displayName = identity.displayName,
    kind = identity.kind,
    // -1: nowhere yet. Block 0 would reopen a document the reader never clicked into with its first
    // block revealed, as though they had been editing the title.
    caret = CaretRecord(blockIndex = -1, offset = 0),
    scrollOffset = 0,
    // Null for an untitled document (7.4): it has no file, so there is nothing to have read yet or
    // ever. The placeholder would claim a file digest it can never have.
    baseDigest = if (identity.uri == null) null else unreadDigest,
    snapshotPath = snapshotPath,
    accessToken = identity.accessToken,
)

/**
 * The digest of a document that has been listed but not read.
 *
 * 8.2's `baseDigest` is recorded at open, which happens after this: the session list knows a
 * document is open before anything has opened it. A digest of nothing is honest and compares
 * unequal to every real document, so a save attempted against it is refused rather than allowed.
 */
private val unreadDigest = sha256(ByteArray(0)).toString()
