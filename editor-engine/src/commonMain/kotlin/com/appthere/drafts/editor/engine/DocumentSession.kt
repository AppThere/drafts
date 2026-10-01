package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.parse.markdown.MarkdownDocumentParser
import kotlin.jvm.JvmName

/**
 * What one edit cost, so the gate can be asserted rather than observed.
 *
 * Phase 2's gate criterion is "reparse range is provably bounded -- asserted in a test, not
 * observed", which needs the engine to *report* what it reparsed. A measurement nobody can read is
 * a claim, not a result.
 */
data class EditOutcome(
    /** The span of the new text that was handed back to the parser. */
    val reparsed: SourceSpan,
    /** How many blocks were replaced. */
    val blocksReplaced: Int,
    /** How many blocks kept their identity, and so their editor state. */
    val blocksReused: Int,
    /** How many blocks after the edit had their spans moved. */
    val blocksShifted: Int,
)

/**
 * An open document: the text, the blocks, and what happens between them when someone types.
 *
 * The architecture under test (`appthere-drafts.md` 4.3): a `LazyColumn` of blocks, one editable
 * field at a time, block boundaries doubling as reparse boundaries. This class is the engine half
 * of that, and the half that decides whether the whole approach is viable -- if a keystroke cannot
 * be made to reparse a bounded amount of text, nothing above it can be fast either.
 *
 * Not a god object, and deliberately so. `engineering-conventions.md` 4.2 predicts exactly what
 * would go wrong: "`DocumentSession` will attract everything: parsing, undo, selection, autosave,
 * file access. Keep it a coordinator that owns collaborators." Undo, selection and persistence are
 * separate concerns and are not here.
 */
class DocumentSession(
    initialText: String,
    private val parser: MarkdownDocumentParser = MarkdownDocumentParser(),
) {
    private val ids = BlockIds()

    var text: String = initialText
        private set

    var blocks: List<EditorBlock> = adopted(parser.parse(initialText).blocks)
        private set

    /**
     * Replaces [range] with [replacement] and reparses as little as it can get away with.
     *
     * The window comes from 4.3: "reparse the dirty block plus enough context to catch constructs
     * that span blocks (list continuation, fenced code, footnote definitions...). A conservative
     * window of 'the enclosing container block, or the paragraph before through the paragraph
     * after' is correct and fast enough."
     *
     * Conservative is the operative word. A window one block wider than strictly necessary costs a
     * fraction of a millisecond; a window one block too narrow silently mis-parses a list that the
     * edit joined to its neighbour, and the user finds out later.
     */
    fun edit(
        range: SourceSpan,
        replacement: String,
    ): EditOutcome {
        val delta = replacement.length - range.length
        val updated = text.replaceRange(range.start.value, range.endExclusive.value, replacement)

        val window = dirtyWindow(range)
        val before = blocks.subList(0, window.first)
        val after = blocks.subList(window.last + 1, blocks.size)

        // Captured before `blocks` is reassigned. An edit that merges two blocks into one leaves
        // the new list shorter than the window, so reading it afterwards indexes off the end --
        // which is exactly what the list-joining test caught.
        val windowIds = window.map { blocks[it].id }.toSet()

        val reparseSpan = reparseSpan(window, delta, updated.length)
        val reparsed = parser.parse(updated.substring(reparseSpan.start.value, reparseSpan.endExclusive.value))

        val replacement0 =
            reparsed.blocks.map { it.shiftedBy(reparseSpan.start.value) }
        val reconciled = reconcile(blocks.subList(window.first, window.last + 1), replacement0)

        text = updated
        blocks = adopted(before + reconciled + after.map { EditorBlock(it.id, it.block.shiftedBy(delta)) })

        return EditOutcome(
            reparsed = reparseSpan,
            blocksReplaced = reconciled.size,
            blocksReused = reconciled.count { it.id in windowIds },
            blocksShifted = after.size,
        )
    }

    /**
     * The index range of blocks to throw away and rebuild: the touched ones, plus one either side.
     *
     * The neighbours are what catch constructs the edit may have joined or split -- typing `- ` at
     * the head of a paragraph below a list makes it a list item, which cannot be seen by looking at
     * that paragraph alone.
     */
    private fun dirtyWindow(range: SourceSpan): IntRange {
        val touched = blocks.indices.filter { index -> blocks[index].touches(range) }

        val first = (touched.minOrNull() ?: blocks.indices.lastOrNull() ?: 0) - 1
        val last = (touched.maxOrNull() ?: blocks.indices.lastOrNull() ?: 0) + 1

        return first.coerceAtLeast(0)..last.coerceAtMost(blocks.lastIndex.coerceAtLeast(0))
    }

    /**
     * The span of the *new* text covered by the dirty window.
     *
     * Read from the old block spans and corrected by the edit's delta, so it needs no second pass
     * over the document. Extended to a line boundary at each end, because a window that begins
     * mid-line would hand the parser a fragment that is not a block.
     */
    private fun reparseSpan(
        window: IntRange,
        delta: Int,
        newLength: Int,
    ): SourceSpan {
        val start =
            blocks[window.first]
                .block.source
                ?.start
                ?.value ?: 0
        val oldEnd =
            blocks[window.last]
                .block.source
                ?.endExclusive
                ?.value ?: text.length

        return SourceSpan.of(
            start.coerceIn(0, newLength),
            (oldEnd + delta).coerceIn(start, newLength),
        )
    }

    /**
     * Gives the rebuilt blocks the identities of the ones they replaced, position by position.
     *
     * Positional matching, not content matching. The common edit is a keystroke inside one
     * paragraph: its content changed, its position did not, and it must keep its id or the field
     * the user is typing into is destroyed and recreated. Matching on content would fail in exactly
     * that case, which is the one that matters.
     */
    private fun reconcile(
        old: List<EditorBlock>,
        rebuilt: List<com.appthere.drafts.core.model.Block>,
    ): List<EditorBlock> =
        rebuilt.mapIndexed { index, block ->
            EditorBlock(id = old.getOrNull(index)?.id ?: ids.next(), block = block)
        }

    /**
     * The blocks as the rest of the application sees them: never none.
     *
     * A document with no text parses to no blocks, and a document with no blocks has nothing to
     * type into -- no field, no caret, no row to tap. `appthere-drafts.md` 7.4 asks for the
     * opposite in as many words: a new document is "ready to type into", and "nothing stands
     * between launching the app and writing". The same state is reached by deleting everything in
     * a document that did have words, where being unable to start again is worse.
     *
     * So an empty document is one empty paragraph covering the empty span at the start. [text] is
     * untouched by this -- it is still "" -- which is what keeps 8.2's digest and the serialiser
     * looking at exactly the bytes that are really there.
     *
     * It keeps its identity through the first keystroke, because [reconcile] matches by position
     * and this block is at position zero: the paragraph the parser then produces inherits its id,
     * so the field the reader is typing into is not destroyed under them after the first character.
     */
    private fun adopted(parsed: List<Block>): List<EditorBlock> =
        parsed
            .ifEmpty { listOf(Paragraph(inlines = emptyList(), source = SourceSpan.of(0, 0))) }
            .map { EditorBlock(ids.next(), it) }

    @JvmName("adoptedBlocks")
    private fun adopted(blocks: List<EditorBlock>): List<EditorBlock> = blocks.ifEmpty { adopted(emptyList<Block>()) }

    private fun EditorBlock.touches(range: SourceSpan): Boolean {
        val span = block.source ?: return false

        // An insertion has zero length and so overlaps nothing; it still belongs to the block whose
        // extent contains it, and to the block it abuts when it lands on a boundary.
        return span.overlaps(range) ||
            range.start in span ||
            range.start.value == span.endExclusive.value ||
            range.endExclusive.value == span.start.value
    }
}

/**
 * The raw source of one block.
 *
 * Straight out of the buffer by span, which is the same mechanism byte-preserving serialisation
 * uses. A block with no span was synthesised rather than parsed and has no source to show.
 */
fun DocumentSession.sourceOf(block: Block): String {
    val span = block.source ?: return ""
    return text.substring(span.start.value, span.endExclusive.value)
}
