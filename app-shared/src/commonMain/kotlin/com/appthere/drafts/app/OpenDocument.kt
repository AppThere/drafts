package com.appthere.drafts.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.engine.UndoHistory
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.files.CaretRecord
import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentContents
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.Recovery
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
    val restored: RestoredSnapshot? = null,
) {
    /** True when the text on screen came from 8.3's snapshot rather than from the file. */
    val restoredFromSnapshot: Boolean get() = restored != null

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
     * True while the restored text is still not in the file.
     *
     * A document opened from an 8.3 snapshot starts unsaved even though nobody has typed: the words
     * on screen are not the words on disk, which is the entire reason the snapshot was kept. The
     * revision counter cannot say that on its own -- it starts at zero either way.
     */
    private var restoredButUnsaved by mutableStateOf(restored != null)

    /**
     * 8.4's state, derived rather than stored.
     *
     * `dirty` is the editor's business and the other four are the store's, so anything that kept a
     * copy of the answer would have two places to update and one chance to disagree. Reading
     * `editor.revision` here is also what subscribes the chrome to it: the label follows the first
     * keystroke without anything having to notify it.
     */
    val lifecycle: DocumentSessionState
        get() = recorded.copy(hasUnsavedEdits = restoredButUnsaved || editor.revision != savedRevision)

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
            restoredButUnsaved = false
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
        restoredButUnsaved = false
        recorded = recorded.reloaded(contents)
    }
}

/**
 * What 8.3 restored, for whoever has to put the reader back where they were.
 *
 * 8.1 keeps `meta.json` beside the snapshot "so caret and scroll survive with the text". The caret
 * is applied to the editor as the document is built; the scroll position cannot be, because the
 * scroll state belongs to the window rather than to the document -- so it is carried here and
 * applied by the window. Null scroll means the record did not say.
 */
data class RestoredSnapshot(
    val scrollOffset: Int?,
)

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
    recover: (suspend (Digest) -> Recovery)? = null,
): DocumentOpening {
    val opening by produceState<DocumentOpening>(DocumentOpening.Opening, store, ref, recover) {
        value =
            runCatching { store.read(ref) }
                .map { contents -> DocumentOpening.Opened(openedWith(store, ref, contents, recover)) }
                .getOrElse { failure ->
                    DocumentOpening.Failed(failure.message ?: failure::class.simpleName.orEmpty())
                }
    }
    return opening
}

/**
 * Builds the document, restoring 8.3's snapshot over it when there is unsaved work in one.
 *
 * The recorded facts stay the *file's* facts even when the text came from the snapshot. That is
 * what keeps 8.2 honest afterwards: the next save compares against the file as it is now, so a
 * restored session that saves over a file someone else has edited is still refused.
 */
private suspend fun openedWith(
    store: DocumentStore,
    ref: DocumentRef,
    contents: DocumentContents,
    recover: (suspend (Digest) -> Recovery)?,
): OpenDocument {
    val recovery = recover?.invoke(contents.facts.digest) ?: Recovery.NothingToRestore
    val restored = recovery as? Recovery.UnsavedWork
    val editor = EditorState(DocumentSession(restored?.text ?: contents.text))

    restored?.record?.caret?.let { editor.placeAt(it) }

    return OpenDocument(
        store = store,
        editor = editor,
        opened = DocumentSessionState.opened(ref, contents),
        restored = restored?.let { RestoredSnapshot(scrollOffset = it.record?.scrollOffset) },
    )
}

/**
 * Puts the caret back where 7.3 recorded it.
 *
 * By index, because that is what 7.3 stores and what survives a restart -- block ids are handed out
 * per session and mean nothing afterwards.
 *
 * An index outside the document is ignored rather than clamped or thrown. A `meta.json` can outlive
 * the text it describes: the snapshot may have been written before an edit that removed blocks, or
 * the record may simply be damaged. Opening at the top is a small loss; failing to open is not.
 */
private fun EditorState.placeAt(caret: CaretRecord) {
    val block = blocks.getOrNull(caret.blockIndex) ?: return
    val offset = caret.offset.coerceIn(0, sourceOf(block.block).length)

    place(Caret(block.id, offset))
}
