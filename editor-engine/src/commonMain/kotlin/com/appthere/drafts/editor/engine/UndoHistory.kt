package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.SourceSpan

/**
 * One reversible change to the text.
 *
 * Recorded against the *text*, not against the block list, for the same reason the structural edits
 * are written that way: the blocks are a function of the text, so restoring the text restores the
 * blocks, and a history of block operations would have to reimplement the parser to know what it
 * had undone.
 *
 * [span] is where the change happened in the text *before* it was applied. Inverting is therefore
 * "replace the inserted text with the removed text at the same place", and redoing is the mirror.
 */
data class Revision(
    val span: SourceSpan,
    val removed: String,
    val inserted: String,
) {
    /** True for a plain typing insertion: nothing taken out, something put in. */
    val isInsertion: Boolean get() = removed.isEmpty() && inserted.isNotEmpty()

    /** True for a plain deletion: something taken out, nothing put in. */
    val isDeletion: Boolean get() = inserted.isEmpty() && removed.isNotEmpty()
}

/**
 * The undo and redo stacks.
 *
 * A separate object on purpose. `engineering-conventions.md` 4.2 names this exact risk:
 * "`DocumentSession` will attract everything: parsing, undo, selection, autosave, file access. Keep
 * it a coordinator that owns collaborators." The session does not know this class exists; it is the
 * caller that records, which also means an edit can deliberately not be recorded -- applying an
 * undo, for one, which would otherwise undo itself forever.
 *
 * **Coalescing is by boundary, not by clock.** Typing a word puts one entry on the stack, not six,
 * because undoing a letter at a time is not undo, it is retyping. Where most editors use a typing
 * timeout, this breaks on the structure of the edit instead -- a run of insertions ends at
 * whitespace, at a caret jump, or when the kind of edit changes. That is deterministic, so what a
 * user gets is what a test asserts, and neither depends on how fast anybody types.
 */
class UndoHistory(
    private val limit: Int = DEFAULT_LIMIT,
) {
    private val undoable = ArrayDeque<Revision>()
    private val redoable = ArrayDeque<Revision>()

    val canUndo: Boolean get() = undoable.isNotEmpty()
    val canRedo: Boolean get() = redoable.isNotEmpty()

    /** How many entries are on the undo stack. Coalescing is visible here and nowhere else. */
    val size: Int get() = undoable.size

    /**
     * Adds a revision, merging it into the previous one when the two are a single act of typing.
     *
     * Recording anything new discards the redo stack: once the document has gone somewhere else,
     * the future that was undone is no longer reachable from here.
     */
    fun record(revision: Revision) {
        redoable.clear()

        val previous = undoable.lastOrNull()
        val merged = previous?.let { merge(it, revision) }

        if (merged != null) {
            undoable.removeLast()
            undoable.addLast(merged)
        } else {
            undoable.addLast(revision)
            if (undoable.size > limit) undoable.removeFirst()
        }
    }

    /** Takes the most recent revision off the undo stack and makes it redoable. */
    fun undo(): Revision? = undoable.removeLastOrNull()?.also { redoable.addLast(it) }

    /** Takes the most recent undone revision back off the redo stack. */
    fun redo(): Revision? = redoable.removeLastOrNull()?.also { undoable.addLast(it) }

    /**
     * The two revisions as one, or null if they are separate acts.
     *
     * A run of typing continues while each character lands immediately after the last. A space or
     * a newline joins the run and then *closes* it, so the next character starts a new entry: undo
     * a word and the space before it stays, leaving the caret at a boundary ready to type again.
     * Letting the space open the next run instead would take it away with the following word, which
     * is the same number of steps but lands somewhere the user did not leave. Backspacing coalesces
     * the same way in the other direction. Anything else -- a caret jump, a deletion after an
     * insertion, a structural edit -- starts a new entry.
     */
    private fun merge(
        previous: Revision,
        next: Revision,
    ): Revision? =
        when {
            previous.isInsertion && next.isInsertion && continuesTyping(previous, next) -> {
                previous.copy(inserted = previous.inserted + next.inserted)
            }

            previous.isDeletion && next.isDeletion && continuesDeleting(previous, next) -> {
                Revision(
                    span = next.span,
                    removed = next.removed + previous.removed,
                    inserted = "",
                )
            }

            else -> {
                null
            }
        }

    private fun continuesTyping(
        previous: Revision,
        next: Revision,
    ): Boolean =
        next.span.start.value == previous.span.start.value + previous.inserted.length &&
            !previous.inserted.last().isWhitespace()

    private fun continuesDeleting(
        previous: Revision,
        next: Revision,
    ): Boolean =
        next.span.endExclusive.value == previous.span.start.value &&
            !previous.removed.first().isWhitespace()

    private companion object {
        /**
         * Entries kept before the oldest is dropped.
         *
         * Bounded because a session can run for days and each entry holds the text it replaced. Two
         * hundred coalesced entries is a long way back -- far more than the handful of steps undo is
         * actually used for, and cheap enough not to think about.
         */
        const val DEFAULT_LIMIT = 200
    }
}

/**
 * Makes an edit and records it, so it can be undone.
 *
 * The removed text is read from the buffer before the edit, which is the only moment it exists in
 * one place. Every edit the user makes should go through here; the ones that should *not* be
 * recorded -- applying an undo or a redo -- call [DocumentSession.edit] directly, which is why that
 * remains the plain operation and this is the decorated one.
 */
fun DocumentSession.editRecording(
    history: UndoHistory,
    range: SourceSpan,
    replacement: String,
): EditOutcome {
    history.record(
        Revision(
            span = range,
            removed = text.substring(range.start.value, range.endExclusive.value),
            inserted = replacement,
        ),
    )

    return edit(range, replacement)
}

/**
 * Undoes the last recorded edit, returning where the caret should go.
 *
 * At the end of what was put back: for a typing run that is the start of the run, because nothing
 * was put back; for an undone deletion it is the end of the restored text, where the caret was when
 * the deletion started. One rule, right in both directions, and no caret needs storing.
 */
fun DocumentSession.undo(history: UndoHistory): Caret? {
    val revision = history.undo() ?: return null
    val at = revision.span.start.value

    edit(SourceSpan.of(at, at + revision.inserted.length), revision.removed)

    return caretAt(at + revision.removed.length)
}

/** Redoes the last undone edit, leaving the caret at the end of what was put back. */
fun DocumentSession.redo(history: UndoHistory): Caret? {
    val revision = history.redo() ?: return null
    val at = revision.span.start.value

    edit(SourceSpan.of(at, at + revision.removed.length), revision.inserted)

    return caretAt(at + revision.inserted.length)
}
