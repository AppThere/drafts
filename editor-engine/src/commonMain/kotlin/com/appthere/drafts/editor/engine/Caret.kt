package com.appthere.drafts.editor.engine

/**
 * Where the caret is: which block, and how far into that block's source.
 *
 * Block-relative rather than document-relative because that is what the UI can act on. Only one
 * field is editable at a time (`appthere-drafts.md` 4.3), and its text is one block's source, so an
 * offset into the document would have to be translated on every keystroke.
 *
 * The translation still has to exist, because structural edits are document-shaped: see [offsetIn]
 * and [caretAt]. The rule this file follows is that a caret is *carried* block-relative and
 * *computed* document-relative.
 */
data class Caret(
    val block: BlockId,
    val offset: Int,
)

/** The document offset of [caret], or null if its block has gone or was never parsed from source. */
fun DocumentSession.offsetIn(caret: Caret): Int? {
    val span = blocks.firstOrNull { it.id == caret.block }?.block?.source ?: return null
    return (span.start.value + caret.offset).coerceIn(span.start.value, span.endExclusive.value)
}

/**
 * The caret at a document offset.
 *
 * This is how focus survives a structural edit. A split or a merge rebuilds the block list, and
 * `DocumentSession` reconciles identities *positionally* -- so after a split the id that used to
 * mean "this paragraph" may now mean the first half of it, and the block the caret should land in
 * may be brand new. An offset has no such problem: it means the same place in the text whatever the
 * parser decided the blocks were.
 *
 * An offset that falls between blocks -- in the blank line that separates them -- belongs to the
 * block before it, at its end. That is where the text it is adjacent to is.
 */
fun DocumentSession.caretAt(documentOffset: Int): Caret? {
    if (blocks.isEmpty()) return null

    val containing = blocks.firstOrNull { it.spans(documentOffset) }
    val preceding = blocks.lastOrNull { it.endsAtOrBefore(documentOffset) }

    return when {
        containing != null -> Caret(containing.id, documentOffset - containing.start())
        preceding != null -> Caret(preceding.id, preceding.length())
        else -> Caret(blocks.first().id, 0)
    }
}

private fun EditorBlock.spans(offset: Int): Boolean {
    val span = block.source ?: return false
    return offset >= span.start.value && offset <= span.endExclusive.value
}

private fun EditorBlock.endsAtOrBefore(offset: Int): Boolean {
    val span = block.source ?: return false
    return span.endExclusive.value <= offset
}

private fun EditorBlock.start(): Int = block.source?.start?.value ?: 0

private fun EditorBlock.length(): Int = block.source?.length ?: 0

/**
 * The caret one block earlier, at the *end* of it.
 *
 * 4.3 lists this as a consequence to design around: "Up-arrow on the first line of a block,
 * down-arrow on the last, Home/End, and backspace at offset 0 (which merges blocks) all require the
 * engine to move focus and place the caret at the correct offset in the neighbour." Leaving a block
 * upwards means arriving at the bottom of the one above, so the caret goes to its end.
 */
fun DocumentSession.caretBefore(block: BlockId): Caret? {
    val index = blocks.indexOfFirst { it.id == block }
    if (index <= 0) return null
    return blocks[index - 1].let { Caret(it.id, it.block.source?.length ?: 0) }
}

/** The caret one block later, at the start of it. The mirror of [caretBefore]. */
fun DocumentSession.caretAfter(block: BlockId): Caret? {
    val index = blocks.indexOfFirst { it.id == block }
    if (index < 0 || index == blocks.lastIndex) return null
    return caretAtStartOf(blocks[index + 1])
}

private fun caretAtStartOf(block: EditorBlock) = Caret(block.id, 0)
