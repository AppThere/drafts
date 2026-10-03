package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockIndex
import com.appthere.drafts.core.model.Document

/** Writes one block's canonical text, in whichever syntax the serialiser using it speaks. */
internal fun interface BlockText {
    fun write(block: Block): String
}

/**
 * What separates two adjacent blocks when neither's separator came out of the file.
 *
 * A constant in Markdown and not in Fountain, where the blank line between blocks is *structural*:
 * a blank line after a character cue ends the speech, so the dialogue under it has to be joined to
 * it by a single newline. Getting this wrong does not merely reflow the file, it reparses as
 * something else.
 */
internal fun interface BlockGap {
    fun between(
        before: Block,
        after: Block,
    ): String
}

/**
 * Serialising a [Document] without rewriting the parts of it nobody touched.
 *
 * `markdown-dialect.md`'s round-trip contract, item 1: "untouched regions are byte-preserved.
 * Retain source spans for every node; re-emit the original bytes for any subtree the user did not
 * edit." `fountain.md` states the same thing more briefly -- "Preserve the source verbatim for
 * untouched regions and you get perfect fidelity for free" -- and it is what separates an editor
 * from a renderer. A renderer may normalise freely; an editor that rewrites a paragraph the user
 * never touched has damaged their file, silently, on a save they did not think twice about.
 *
 * So two paths:
 *
 * - A block that still carries its source span, when the source is available, is re-emitted as the
 *   exact bytes it came from. Unusual spacing, a four-space-indented list, a transition typed forty
 *   columns to the right -- all preserved, because none of it was read.
 * - Anything else is written from the IR by [text], in the canonical style the format prescribes.
 *
 * The gaps between blocks are emitted verbatim too. Blank lines, trailing whitespace and the
 * document's final newline all live between spans rather than inside them, and dropping them would
 * make byte-identity impossible no matter how exact the spans were.
 *
 * None of this is Markdown-specific, which is why it is here rather than in either serialiser. The
 * two formats differ in how a block is spelled and in what goes between two of them; they do not
 * differ at all in what byte-preservation means.
 */
internal class SourcePreserving(
    private val text: BlockText,
    private val gap: BlockGap,
) {
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
        source: String?,
        rewritten: Set<BlockIndex>,
    ): String =
        if (source == null) {
            canonical(document)
        } else {
            preserving(document, source, rewritten)
        }

    /** Every block from the IR, with nothing of the original file in it. */
    private fun canonical(document: Document): String =
        buildString {
            var previous: Block? = null

            document.blocks.forEach { block ->
                previous?.let { append(gap.between(it, block)) }
                append(text.write(block))
                previous = block
            }
        }

    private fun preserving(
        document: Document,
        source: String,
        rewritten: Set<BlockIndex>,
    ): String =
        buildString {
            var cursor = 0
            var previous: Block? = null

            document.blocks.forEachIndexed { index, block ->
                cursor = appendBlock(block, previous, BlockIndex(index) in rewritten, source, cursor)
                previous = block
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
        previous: Block?,
        isRewritten: Boolean,
        source: String,
        cursor: Int,
    ): Int {
        val span = block.source
        if (span == null) {
            // Inserted: it was never in the file, so it has no region to replace.
            appendInsertion(block, previous)
            return cursor
        }

        // Everything between the previous block and this one -- blank lines, indentation the parser
        // did not claim -- is part of the file and belongs in the output.
        if (span.start.value > cursor) {
            append(source, cursor, span.start.value)
        }

        if (isRewritten) {
            append(text.write(block))
        } else {
            append(source, span.start.value, span.endExclusive.value)
        }

        // Either way the cursor moves past the block's original extent, so the bytes it occupied are
        // consumed rather than re-emitted after it.
        return span.endExclusive.value
    }

    /** Places a block that has no source position of its own, separated from what precedes it. */
    private fun StringBuilder.appendInsertion(
        block: Block,
        previous: Block?,
    ) {
        val separator = previous?.let { gap.between(it, block) }

        if (separator != null && isNotEmpty() && !endsWith(separator)) {
            append(separator)
        }

        append(text.write(block))
    }
}
