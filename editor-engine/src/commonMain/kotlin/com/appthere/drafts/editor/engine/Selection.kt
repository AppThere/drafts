package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.SourceSpan

/**
 * A selection: where it started and where it is now.
 *
 * `appthere-drafts.md` 4.4 specifies the shape -- "Maintain selection as `(blockId, offset)` anchor
 * and focus in the engine" -- and calls the whole problem "the single largest unknown in the
 * project", because Compose's own selection does not compose with editable fields and no single
 * text layout spans more than one block.
 *
 * Anchor and focus rather than start and end. Which end is which depends on the direction of the
 * drag, and the user can drag backwards through the document; the ordering is worked out when the
 * selection is *used*, not when it is recorded, so that extending it never has to notice it has
 * crossed over itself.
 */
data class Selection(
    val anchor: Caret,
    val focus: Caret,
) {
    /** Nothing is selected; this is just a caret. */
    val isCollapsed: Boolean get() = anchor == focus

    companion object {
        fun at(caret: Caret) = Selection(caret, caret)
    }
}

/**
 * The span of document the selection covers, in document order.
 *
 * Null when either end has gone -- its block was deleted by an edit the selection outlived.
 */
fun DocumentSession.spanOf(selection: Selection): SourceSpan? {
    val anchor = offsetIn(selection.anchor)
    val focus = offsetIn(selection.focus)

    return if (anchor == null || focus == null) {
        null
    } else {
        SourceSpan.of(minOf(anchor, focus), maxOf(anchor, focus))
    }
}

/**
 * The raw source the selection covers -- what copy puts on the clipboard.
 *
 * 4.4 is explicit that a multi-block copy "yields the raw source of those blocks", and taking it
 * straight out of the buffer is what makes that true without any work: the blank lines between
 * blocks come along because they are between the offsets, and the markup comes along because it was
 * never removed from the text, only from the preview.
 */
fun DocumentSession.textIn(selection: Selection): String {
    val span = spanOf(selection) ?: return ""
    return text.substring(span.start.value, span.endExclusive.value)
}

/**
 * Deletes what the selection covers, returning the caret left behind.
 *
 * Against the source, not against any text field. 4.4: "implement copy/cut/delete against the IR
 * rather than against any text field" -- a field only ever holds one block, and the point of this
 * layer is the selections that do not fit in one.
 */
fun DocumentSession.delete(
    selection: Selection,
    history: UndoHistory,
): Caret? {
    val span = spanOf(selection) ?: return null
    if (span.length > 0) editRecording(history, span, "")

    return caretAt(span.start.value)
}

/** Everything, from the first character of the first block to the last of the last. */
fun DocumentSession.selectAll(): Selection? {
    val first = blocks.firstOrNull() ?: return null
    val last = blocks.last()
    return Selection(
        anchor = Caret(first.id, 0),
        focus = Caret(last.id, last.block.source?.length ?: 0),
    )
}
