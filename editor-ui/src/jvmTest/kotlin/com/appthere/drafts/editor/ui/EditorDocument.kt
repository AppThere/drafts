package com.appthere.drafts.editor.ui

import com.appthere.drafts.editor.engine.DocumentSession

/** Builds the little documents the UI tests open, so the tests read as documents, not strings. */
internal object EditorDocument {
    fun session(vararg blocks: String): DocumentSession = DocumentSession(blocks.joinToString("\n\n") + "\n")
}
