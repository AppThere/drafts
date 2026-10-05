package com.appthere.drafts.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.editor.engine.BlockParser
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
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.intents.DocumentKind

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
    reopening: Reopening = Reopening(),
) {
    /** True when the text on screen came from 8.3's snapshot rather than from the file. */
    val restoredFromSnapshot: Boolean = reopening.fromSnapshot

    /** Where 7.3's record says the window was scrolled to, or null if it did not say. */
    val scrollOffset: Int? = reopening.scrollOffset

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
    private var restoredButUnsaved by mutableStateOf(restoredFromSnapshot)

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

    /** True for a document with no file yet (7.4), whose first save has to be [saveAs]. */
    val isUntitled: Boolean get() = recorded.ref == null

    /**
     * True when there is no file for [save] to write to: an untitled document, or one whose file
     * vanished before it could be read (7.3). Either way the next save has to choose where.
     */
    val needsSaveAs: Boolean get() = recorded.base == null

    /**
     * 8.2's explicit save: re-read, compare, and write only if the file is untouched.
     *
     * [savedRevision] moves only when the bytes are actually down. A save that was refused, or that
     * failed on a full disk, leaves the document dirty -- which is true, and is what keeps 8.1's
     * snapshot the thing standing between the reader and losing work.
     *
     * Null for an untitled document. It has no file to write to, so there was no save to have an
     * outcome; the caller's answer is [saveAs].
     */
    suspend fun save(): WriteOutcome? {
        val ref = recorded.ref
        val base = recorded.base
        if (ref == null || base == null) return null

        val outcome = store.writeIfUnchanged(ref, editor.text, base.digest)
        if (outcome is WriteOutcome.Written) {
            savedRevision = editor.revision
            restoredButUnsaved = false
        }
        recorded = recorded.wrote(outcome)
        return outcome
    }

    /**
     * 7.4's *Save As*: puts the words in [ref], and from then on this is an ordinary file-backed
     * document.
     *
     * No digest check, because there is nothing to have changed underneath: [ref] was chosen a
     * moment ago through the platform's own picker, which asks before replacing a file that is
     * already there. The write is still atomic -- no write to a reader's file is anything else.
     */
    suspend fun saveAs(ref: DocumentRef): WriteOutcome {
        val outcome = store.writeAtomically(ref, editor.text)
        if (outcome is WriteOutcome.Written) {
            savedRevision = editor.revision
            restoredButUnsaved = false
            recorded = recorded.savedAs(ref, outcome.facts)
        }
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
        // An untitled document has no file to go back to; its words are already the only copy.
        val ref = recorded.ref ?: return
        val contents = runCatching { store.read(ref) }.getOrNull()

        if (contents == null) {
            recorded = recorded.wrote(WriteOutcome.Unavailable(WriteOutcome.Reason.Missing, "could not be re-read"))
            return
        }

        editor = editor.reloaded(contents.text)
        savedRevision = editor.revision
        restoredButUnsaved = false
        recorded = recorded.reloaded(contents)
    }
}

/**
 * What a previous session left behind for this one.
 *
 * Two separate facts. Whether the words came from 8.3's snapshot decides the restore banner; where
 * 7.3's record says the reader was decides where the window opens -- and holds whether or not any
 * words were restored, because a document that was only read still reopens where it was left. The
 * scroll is carried here rather than applied, because scroll state belongs to the window.
 */
data class Reopening(
    val fromSnapshot: Boolean = false,
    val scrollOffset: Int? = null,
)

/**
 * An untitled document (7.4): a new one, or one restored from its snapshot.
 *
 * [text] is the snapshot's, or empty for a new document; [record] is 7.3's, when there is one, and
 * puts the caret and scroll back where the reader left them. There is no restore banner: the badge
 * already says the words are not in a file, and the banner's *Discard* -- back to the file -- has
 * no file to go back to.
 */
fun openUntitled(
    store: DocumentStore,
    text: String = "",
    record: SessionRecord? = null,
    keywords: FountainKeywords = FountainKeywords.ENGLISH,
): OpenDocument {
    val editor = EditorState(DocumentSession(text, blockParserFor(record?.kind, keywords)))
    record?.caret?.let { editor.placeAt(it) }

    // 7.4: "ready to type into", and "Nothing stands between launching the app and writing."
    //
    // Only a document with no words at all. One that opens with prose in it is one the reader is
    // coming back to, and 7.3 puts their caret back where they left it -- or leaves it nowhere,
    // which is right: a caret blinking in a paragraph nobody asked to edit invites an accidental
    // keystroke. A document with nothing in it has nothing else to be looking at.
    if (editor.caret == null && editor.text.isEmpty()) {
        editor.blocks.firstOrNull()?.let { editor.place(Caret(it.id, 0)) }
    }

    return OpenDocument(
        store = store,
        editor = editor,
        opened = DocumentSessionState.untitled,
        reopening = Reopening(scrollOffset = record?.scrollOffset),
    )
}

/** Whether the document has arrived yet. Opening is I/O, so there is a moment before it has. */
sealed interface DocumentOpening {
    /** Reading. Brief for a local file, not for one a sync provider has to fetch first. */
    data object Opening : DocumentOpening

    data class Opened(
        val document: OpenDocument,
    ) : DocumentOpening

    /** There is no document to show, and [reason] says why in terms the reader can act on. */
    data class Failed(
        val reason: Reason,
    ) : DocumentOpening

    enum class Reason {
        /** The file is not there: deleted, moved, or on a drive that has gone (7.3). */
        Missing,

        /** The file is there and could not be read: permissions, a lock, a failing disk. */
        Unreadable,

        /** The file is not there, and no snapshot of its words was ever kept either. */
        NothingKept,
    }
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
    kind: String? = null,
    keywords: suspend () -> FountainKeywords = { FountainKeywords.ENGLISH },
): DocumentOpening {
    // Not a key: a lambda is a new value on every composition, and opening the document again for
    // each would throw away the reader's edits. The words are read once, as the document opens.
    val words by rememberUpdatedState(keywords)
    val opening by produceState<DocumentOpening>(DocumentOpening.Opening, store, ref, recover, kind) {
        value =
            runCatching { store.read(ref) }
                .map { contents -> DocumentOpening.Opened(openedWith(store, ref, contents, recover, kind, words())) }
                .getOrElse {
                    // Gone, or there and unreadable: the two need different answers.
                    val there = runCatching { store.exists(ref) }.getOrDefault(false)
                    DocumentOpening.Failed(
                        if (there) DocumentOpening.Reason.Unreadable else DocumentOpening.Reason.Missing,
                    )
                }
    }
    return opening
}

/**
 * A document whose file vanished, reopened from its snapshot (7.3).
 *
 * "A document whose file has vanished opens read-only from its snapshot with a clear banner
 * offering *Save As*." It keeps the file it came from, so the badge says *File missing* and Save As
 * offers its old name. It has no facts of that file to compare a save against, which is what sends
 * its next save to *Save As*. Editing stays possible, as it does for any `readOnly` document -- the
 * reader may be about to save these words somewhere new.
 */
fun openVanished(
    store: DocumentStore,
    ref: DocumentRef,
    text: String,
    record: SessionRecord? = null,
    keywords: FountainKeywords = FountainKeywords.ENGLISH,
): OpenDocument {
    val editor = EditorState(DocumentSession(text, blockParserFor(record?.kind, keywords)))
    record?.caret?.let { editor.placeAt(it) }

    return OpenDocument(
        store = store,
        editor = editor,
        opened = DocumentSessionState(ref = ref, base = null, unreachable = true),
        reopening = Reopening(scrollOffset = record?.scrollOffset),
    )
}

/**
 * Opens a restored untitled session (7.4) from its snapshot, off the composition thread.
 *
 * The snapshot is the only copy of an untitled document's words, so it is read rather than
 * compared with anything. A session with no snapshot yet -- one opened and closed before 8.1 had a
 * reason to write -- opens empty, which is what it was.
 */
@Composable
fun rememberUntitledDocument(
    store: DocumentStore,
    snapshots: SnapshotStore,
    documentId: String,
    keywords: suspend () -> FountainKeywords = { FountainKeywords.ENGLISH },
): DocumentOpening {
    val words by rememberUpdatedState(keywords)
    val opening by produceState<DocumentOpening>(DocumentOpening.Opening, store, snapshots, documentId) {
        val text = snapshots.textOf(documentId).orEmpty()
        value = DocumentOpening.Opened(openUntitled(store, text, snapshots.recordOf(documentId), words()))
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
    kind: String?,
    keywords: FountainKeywords,
): OpenDocument {
    val recovery = recover?.invoke(contents.facts.digest) ?: Recovery.NothingToRestore()
    val restored = recovery as? Recovery.UnsavedWork
    val editor = EditorState(DocumentSession(restored?.text ?: contents.text, blockParserFor(kind, keywords)))

    // 7.3's caret and scroll, whichever way the document opened. They used to come back only with
    // unsaved work, so a document that had been read and closed reopened at the top every time.
    recovery.record?.caret?.let { editor.placeAt(it) }

    return OpenDocument(
        store = store,
        editor = editor,
        opened = DocumentSessionState.opened(ref, contents),
        reopening = Reopening(fromSnapshot = restored != null, scrollOffset = recovery.record?.scrollOffset),
    )
}

/**
 * How a document of [kind] is read: 9.1's two grammars. Markdown when nothing says -- the sample
 * document, a file of no recognised extension, a buffer that never had a kind. A screenplay is read
 * with [keywords], its own scene-heading words (11.3).
 */
fun blockParserFor(
    kind: String?,
    keywords: FountainKeywords = FountainKeywords.ENGLISH,
): BlockParser =
    when (kind?.let(::kindOf)) {
        DocumentKind.Fountain -> BlockParser.Fountain(keywords)
        else -> BlockParser.Markdown()
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
