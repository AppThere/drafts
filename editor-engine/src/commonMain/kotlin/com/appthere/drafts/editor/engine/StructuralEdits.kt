package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.SourceSpan

// The two edits that change the *shape* of the document rather than its words.
//
// `appthere-drafts.md` 4.3: "Typing can change block structure. Typing `## ` at the start of a
// paragraph promotes it to a heading; pressing Enter splits; backspacing at offset 0 merges. The
// engine reparses the affected range and reconciles the block list, preserving focus and caret
// offset across the structural change."
//
// Promotion needs no code here -- it falls out of reparsing the window, which is why
// `DocumentSessionTest` can already assert it. Splitting and merging do, because neither is
// something the focused text field can express: the field holds one block's source and both edits
// reach outside it.
//
// Both record, so Enter and Backspace can be undone as the single acts they are. An edit that is
// not recorded is a bug rather than a choice, so the history is a parameter and not a default.
//
// Both are written as edits to the *text*, not to the block list. Inserting a blank line is what
// splitting a paragraph means in Markdown; deleting one is what merging means. Manipulating the
// parsed blocks directly would produce a block list that the source no longer implies, and the next
// reparse would silently disagree with it.

/**
 * Splits the block at [caret] in two, returning the caret at the head of the second half.
 *
 * Splitting mid-word in a paragraph gives two paragraphs; splitting a heading gives a heading and a
 * paragraph, because that is what the resulting source parses as. The engine deliberately does not
 * try to be cleverer than the text -- continuing a list on Enter means inserting the next marker,
 * which is a Fountain/Markdown authoring behaviour for Phase 4 rather than a caret concern.
 *
 * What Enter inserts is the grammar's to say ([BlockParser.enterAt]): a blank line in most places,
 * one line break inside a code fence or under a character's name.
 */
fun DocumentSession.split(
    caret: Caret,
    history: UndoHistory,
): Caret? {
    val block = blocks.firstOrNull { it.id == caret.block }?.block
    val at = offsetIn(caret)
    if (block == null || at == null) return null

    val enter = enterAt(at, block)
    val next = at + enter.inserted.length

    editRecording(history, SourceSpan.of(at, at), enter.inserted)
    enter.opens?.let { openLine(next, it) }

    return caretAt(next)
}

/**
 * Merges the block at [caret] into the one above it, returning the caret at the join.
 *
 * Null when there is nothing above to merge into, which is the UI's signal to let Backspace do its
 * ordinary thing (nothing, at the start of the document).
 *
 * The span deleted is everything *between* the two blocks, whatever that turns out to be -- one
 * blank line usually, several if the author left them, and the leading marker of nothing at all.
 * Taking it from the spans rather than assuming `"\n\n"` is what makes this correct on a document
 * someone else wrote.
 */
fun DocumentSession.mergeWithPrevious(
    caret: Caret,
    history: UndoHistory,
): Caret? {
    val index = blocks.indexOfFirst { it.id == caret.block }
    val previous = blocks.getOrNull(index - 1)?.block?.source
    val current = blocks.getOrNull(index)?.block?.source

    if (previous == null || current == null || previous.endExclusive.value >= current.start.value) {
        return null
    }

    editRecording(history, SourceSpan.of(previous.endExclusive.value, current.start.value), "")

    return caretAt(previous.endExclusive.value)
}
