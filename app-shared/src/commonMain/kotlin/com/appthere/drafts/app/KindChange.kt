package com.appthere.drafts.app

import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.SessionList

/**
 * 7.4's choice of kind for an untitled document, carried to everything that records it.
 *
 * "Until the first save that label is a control; choosing Fountain re-interprets the same text as
 * Fountain and applies the Fountain reader settings." The open document is read again in the new
 * kind's grammar, with its words untouched. The session record takes the new kind, so the
 * next launch restores it as what the reader chose; the keeper takes it, so the next autosave does
 * not write the old one back; and it becomes the kind the next new document starts as.
 *
 * Only for an untitled document. Once there is a file, its extension is its kind (9.1).
 */
class KindChange(
    private val sessions: SessionList,
    private val settings: SettingsStore,
    private val onMoved: (SessionRecord) -> Unit,
) {
    suspend fun to(
        kind: DocumentKind,
        record: SessionRecord,
        keeper: SnapshotKeeper?,
        editor: EditorState,
    ) {
        require(record.uri == null) { "A document with a file takes its kind from the file's extension" }

        editor.reinterpretAs(blockParserFor(kind.id, settings.keywordsFor(record.identity())))

        val identity = record.identity().copy(kind = kind.id)
        keeper?.movedTo(identity)
        sessions.updated(identity)?.let(onMoved)
        settings.rememberKindForNew(kind)
    }
}
