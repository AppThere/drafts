package com.appthere.drafts.app

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.windows.SessionList

/**
 * 7.4's *Save As*, once the reader has chosen where.
 *
 * "The first save is *Save As*, through the platform's own picker (`ACTION_CREATE_DOCUMENT` on
 * Android ...; the export picker on iOS; a save dialog on desktop)."
 *
 * The picker is the host's -- it is the one part of this that is different on every platform -- and
 * so is turning what it returns into a [SessionIdentity]: a path on the desktop, a `content://` URI
 * with a persisted grant on Android. What happens next is the same everywhere, and is here.
 *
 * **The document keeps its id.** Its snapshot directory and its session are its own, and a new id
 * would orphan both (`divergences.md`, 7.3). [destination] is taken for everything that says where
 * the document lives -- the file, the name in the chrome, the kind its extension implies -- and its
 * `documentId` is overwritten with the one the document already has, so a host cannot get that
 * wrong by forgetting to carry it across.
 *
 * The keeper moves too, or the next autosave would write the old location back over the new one;
 * and the record moves, so the next launch reopens the file rather than an untitled document.
 *
 * [onMoved] hands the updated record to whoever is showing it: the chrome's name is its name.
 */
class SaveAs(
    private val sessions: SessionList,
    private val onMoved: (SessionRecord) -> Unit,
) {
    suspend fun to(
        destination: SessionIdentity,
        document: OpenDocument,
        record: SessionRecord,
        keeper: SnapshotKeeper?,
    ): WriteOutcome {
        val moved = destination.copy(documentId = record.documentId)
        val where = moved.accessToken ?: moved.uri

        require(where != null) { "Save As needs somewhere to save to" }

        val outcome = document.saveAs(DocumentRef(where))
        if (outcome is WriteOutcome.Written) {
            keeper?.movedTo(moved)
            sessions.updated(moved)?.let(onMoved)
        }
        return outcome
    }
}
