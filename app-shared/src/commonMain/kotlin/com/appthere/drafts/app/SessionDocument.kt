package com.appthere.drafts.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.Recovery
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore

/**
 * A session's document, opened whichever way it has to be.
 *
 * - With a file, from the file, with 8.3's snapshot restored over it if that holds unsaved work.
 * - Untitled (7.4), from its snapshot, the only copy of its words there is.
 * - With a file that has vanished (7.3), from its snapshot, as a document whose next save chooses
 *   where -- and if nothing was ever kept, a [DocumentOpening.Failed] that says so.
 *
 * Decided once per session, when its window opens. After *Save As* the record has a file, and
 * deciding again would re-read the document from it: a new editor, with the undo history and the
 * caret gone, for a document that had not changed.
 */
@Composable
fun rememberSessionDocument(
    store: DocumentStore,
    snapshots: SnapshotStore,
    record: SessionRecord,
): DocumentOpening {
    val file = remember(record.documentId) { record.accessToken ?: record.uri }
    if (file == null) return rememberUntitledDocument(store, snapshots, record.documentId)

    val ref = remember(file) { DocumentRef(file) }
    val recover: suspend (Digest) -> Recovery =
        remember(record.documentId) { { digest -> snapshots.examine(record.documentId, digest) } }

    val opening = rememberOpenDocument(store, ref, recover, record.kind)
    val missing = opening is DocumentOpening.Failed && opening.reason == DocumentOpening.Reason.Missing

    return if (missing) rememberVanishedDocument(store, snapshots, ref, record.documentId) else opening
}

/** 7.3's vanished file: its snapshot's words, or word that there are none. */
@Composable
private fun rememberVanishedDocument(
    store: DocumentStore,
    snapshots: SnapshotStore,
    ref: DocumentRef,
    documentId: String,
): DocumentOpening {
    val opening by produceState<DocumentOpening>(DocumentOpening.Opening, store, snapshots, ref, documentId) {
        val text = snapshots.textOf(documentId)
        value =
            if (text == null) {
                DocumentOpening.Failed(DocumentOpening.Reason.NothingKept)
            } else {
                DocumentOpening.Opened(openVanished(store, ref, text, snapshots.recordOf(documentId)))
            }
    }
    return opening
}
