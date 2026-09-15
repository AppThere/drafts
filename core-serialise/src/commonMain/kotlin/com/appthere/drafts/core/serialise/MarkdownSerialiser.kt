package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockIndex
import com.appthere.drafts.core.model.Document

/**
 * [Document] out to Markdown, source-preserving.
 *
 * `markdown-dialect.md`'s round-trip contract, item 1: "untouched regions are byte-preserved.
 * Retain source spans for every node; re-emit the original bytes for any subtree the user did not
 * edit." That is the whole design here, and it is what separates an editor from a renderer. A
 * renderer may normalise freely; an editor that rewrites a paragraph the user never touched has
 * damaged their file, silently, on a save they did not think twice about.
 *
 * So two paths:
 *
 * - A block that still carries its source span, when the source is available, is re-emitted as the
 *   exact bytes it came from. Unusual spacing, a four-space-indented list, a Setext heading with a
 *   sixty-character rule -- all preserved, because none of it was read.
 * - Anything else is written from the IR by [BlockWriter], in the canonical style the contract
 *   prescribes.
 *
 * The gaps between blocks are emitted verbatim too. Blank lines, trailing whitespace and the
 * document's final newline all live between spans rather than inside them, and dropping them would
 * make byte-identity impossible no matter how exact the spans were.
 */
class MarkdownSerialiser {
    private val blocks = BlockWriter(InlineWriter())

    /**
     * Serialises [document].
     *
     * @param source the text the document was parsed from. Supplying it enables byte-preservation;
     *   without it every block is written canonically, which is correct but not byte-identical.
     *   The caller holds this already -- the editor keeps the buffer for incremental reparse.
     * @param rewritten blocks whose content no longer matches the source and must therefore be
     *   written from the IR, even though they still know where they came from.
     *
     * A block's span and its freshness are two different facts, and conflating them was a real bug
     * here. The span says *where the block lives in the file*; [rewritten] says *whether its text
     * is still the file's*. A block with a span that is not rewritten is copied verbatim; one that
     * is rewritten replaces exactly its own span; and a block with no span at all was never in the
     * file, so it is an insertion and is placed at the cursor.
     */
    fun serialise(
        document: Document,
        source: String? = null,
        rewritten: Set<BlockIndex> = emptySet(),
    ): String =
        if (source == null) {
            document.blocks.joinToString(BLOCK_SEPARATOR) { blocks.write(it) }
        } else {
            preserving(document, source, rewritten)
        }

    private fun preserving(
        document: Document,
        source: String,
        rewritten: Set<BlockIndex>,
    ): String =
        buildString {
            var cursor = 0

            document.blocks.forEachIndexed { index, block ->
                cursor = appendBlock(block, BlockIndex(index) in rewritten, source, cursor)
            }

            // The tail: trailing blank lines, and the final newline most files end with.
            if (cursor < source.length) {
                append(source, cursor, source.length)
            }
        }

    /**
     * Appends one block and returns the new cursor -- the offset in [source] that has been consumed.
     *
     * Named rather than inlined into the loop above: with the three cases written out in place, the
     * nesting alone pushed the enclosing function past the cognitive-complexity limit, and the three
     * cases are the whole design of this class. They deserve to be readable.
     */
    private fun StringBuilder.appendBlock(
        block: Block,
        isRewritten: Boolean,
        source: String,
        cursor: Int,
    ): Int {
        val span = block.source
        if (span == null) {
            // Inserted: it was never in the file, so it has no region to replace.
            appendInsertion(block)
            return cursor
        }

        // Everything between the previous block and this one -- blank lines, indentation the parser
        // did not claim -- is part of the file and belongs in the output.
        if (span.start.value > cursor) {
            append(source, cursor, span.start.value)
        }

        if (isRewritten) {
            append(blocks.write(block))
        } else {
            append(source, span.start.value, span.endExclusive.value)
        }

        // Either way the cursor moves past the block's original extent, so the bytes it occupied are
        // consumed rather than re-emitted after it.
        return span.endExclusive.value
    }

    /** Places a block that has no source position of its own, separated from what precedes it. */
    private fun StringBuilder.appendInsertion(block: Block) {
        if (isNotEmpty() && !endsWith(BLOCK_SEPARATOR)) {
            append(BLOCK_SEPARATOR)
        }
        append(blocks.write(block))
    }

    private companion object {
        const val BLOCK_SEPARATOR = "\n\n"
    }
}
