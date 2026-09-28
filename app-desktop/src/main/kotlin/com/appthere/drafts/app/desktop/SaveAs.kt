package com.appthere.drafts.app.desktop

import com.appthere.drafts.app.OpenDocument
import com.appthere.drafts.app.SnapshotKeeper
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.SessionList

/**
 * 7.4's *Save As* on the desktop, once the reader has chosen where.
 *
 * The document keeps its id. Its snapshot directory and its session are its own, and a new id would
 * orphan both (`divergences.md`, 7.3). What moves is everything that says where it lives -- the
 * file, the name in the title bar, the kind the extension implies -- in the record, so the next
 * launch reopens the file, and in the keeper, so the next autosave does not write the old location
 * back over it.
 *
 * [onMoved] hands the updated record to whoever is showing it: the window's title is its name.
 */
internal class SaveAs(
    private val sessions: SessionList,
    private val onMoved: (SessionRecord) -> Unit,
) {
    suspend fun to(
        path: String,
        document: OpenDocument,
        record: SessionRecord,
        keeper: SnapshotKeeper?,
    ): WriteOutcome {
        // The extension the reader chose decides the kind (9.1); a name with none keeps the old one.
        val kind = DocumentKind.of(path)?.id ?: record.kind
        val moved = desktopIdentity(path, kind).copy(documentId = record.documentId)

        val outcome = document.saveAs(DocumentRef(moved.accessToken ?: path))
        if (outcome is WriteOutcome.Written) {
            keeper?.movedTo(moved)
            sessions.savedAs(moved)?.let(onMoved)
        }
        return outcome
    }
}
