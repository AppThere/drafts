package com.appthere.drafts.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.engine.UndoHistory
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
    editor: EditorState,
    opened: DocumentSessionState,
) {
    /**
     * Replaced wholesale by [reload], never edited in place.
     *
     * Reloading is 8.2's "lose my changes", and a fresh [EditorState] is what makes that true: a new
     * [DocumentSession] parsed from the file's bytes and a new, empty [UndoHistory]. Reusing the old
     * one would leave the reader able to press Ctrl+Z and resurrect the very changes they just chose
     * to discard, on top of a document that is no longer the one they were made against.
     */
    var editor: EditorState by mutableStateOf(editor)
        private set

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

    /**
     * 8.2's "[ Reload and lose my changes ]".
     *
     * The only exit from `conflicted` that keeps the reader in the same document. It clears
     * `orphaned` too, because a file that could be read is by definition no longer missing -- a
     * sync client restoring a folder is ordinary, and making the reader close and reopen the
     * document to escape a state they did not cause would be a poor reward for it.
     *
     * A reload that cannot read the file leaves the session unreachable rather than throwing. The
     * reader asked to see what is on disk; being told there is nothing there is an answer.
     */
    suspend fun reload() {
        val contents = runCatching { store.read(recorded.ref) }.getOrNull()

        if (contents == null) {
            recorded = recorded.wrote(WriteOutcome.Unavailable(WriteOutcome.Reason.Missing, "could not be re-read"))
            return
        }

        editor = EditorState(DocumentSession(contents.text))
        savedRevision = editor.revision
        recorded = recorded.reloaded(contents)
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
