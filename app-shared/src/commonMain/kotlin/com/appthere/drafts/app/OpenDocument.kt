package com.appthere.drafts.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.WriteOutcome

/**
 * One open document: the text being edited, and where it came from.
 *
 * This is the seam `appthere-drafts.md` 8.4 needs and neither side can provide alone.
 * [DocumentSessionState] knows about files and nothing about editing; [EditorState] knows about
 * editing and, deliberately, nothing about files. The state the reader is shown depends on both.
 */
@Stable
class OpenDocument(
    private val store: DocumentStore,
    val editor: EditorState,
    opened: DocumentSessionState,
) {
    private var recorded by mutableStateOf(opened)
    private var savedRevision by mutableStateOf(editor.revision)

    /**
     * 8.4's state, derived rather than stored.
     *
     * `dirty` is the editor's business and the other four are the store's, so anything that kept a
     * copy of the answer would have two places to update and one chance to disagree. Reading
     * `editor.revision` here is also what subscribes the chrome to it: the label follows the first
     * keystroke without anything having to notify it.
     */
    val lifecycle: DocumentSessionState
        get() = recorded.copy(hasUnsavedEdits = editor.revision != savedRevision)

    /**
     * 8.2's explicit save: re-read, compare, and write only if the file is untouched.
     *
     * [savedRevision] moves only when the bytes are actually down. A save that was refused, or that
     * failed on a full disk, leaves the document dirty -- which is true, and is what keeps 8.1's
     * snapshot the thing standing between the reader and losing work.
     */
    suspend fun save(): WriteOutcome {
        val outcome = store.writeIfUnchanged(recorded.ref, editor.text, recorded.base.digest)
        if (outcome is WriteOutcome.Written) {
            savedRevision = editor.revision
        }
        recorded = recorded.wrote(outcome)
        return outcome
    }
}

/** Whether the document has arrived yet. Opening is I/O, so there is a moment before it has. */
sealed interface DocumentOpening {
    /** Reading. Brief for a local file, not for one a sync provider has to fetch first. */
    data object Opening : DocumentOpening

    data class Opened(
        val document: OpenDocument,
    ) : DocumentOpening

    /** The file could not be read at all, so there is no session to put into a state. */
    data class Failed(
        val detail: String,
    ) : DocumentOpening
}

/**
 * Opens [ref] through [store], off the composition thread.
 *
 * 8.2's "At open, record `baseDigest` (SHA-256 of file contents), size, and mtime" happens here,
 * once, and is the only place it can happen -- which is what stops a later save comparing against
 * a digest nobody recorded.
 *
 * A failure is a value rather than an exception. Being handed a path that has gone away is ordinary
 * -- a restored session pointing at a deleted file, per 8.3 -- and the reader needs to be told,
 * not to watch the window fail to appear.
 */
@Composable
fun rememberOpenDocument(
    store: DocumentStore,
    ref: DocumentRef,
): DocumentOpening {
    val opening by produceState<DocumentOpening>(DocumentOpening.Opening, store, ref) {
        value =
            runCatching { store.read(ref) }
                .map { contents ->
                    DocumentOpening.Opened(
                        OpenDocument(
                            store = store,
                            editor = EditorState(DocumentSession(contents.text)),
                            opened = DocumentSessionState.opened(ref, contents),
                        ),
                    )
                }.getOrElse { failure ->
                    DocumentOpening.Failed(failure.message ?: failure::class.simpleName.orEmpty())
                }
    }
    return opening
}
