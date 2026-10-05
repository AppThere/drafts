package com.appthere.drafts.editor.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.editor.engine.EditorBlock

/**
 * Two speeches spoken at once: `fountain.md`'s dual dialogue, which 5.4 sets side by side.
 *
 * [first] and [second] are block indices, each a character cue and the parentheticals and dialogue
 * under it. Only the second is marked in the file -- `^` on its name -- so the first is whichever
 * speech comes immediately before it. First and second rather than left and right: in a right-to-left
 * script the first speech is the one on the right.
 */
@Immutable
internal data class DualPair(
    val first: IntRange,
    val second: IntRange,
) {
    val blocks: IntRange get() = first.first..second.last

    /** Where block [index] sits in the pair, which decides what a stacked pair draws beside it. */
    fun partOf(index: Int): DualPart =
        when (index) {
            first.first -> DualPart.Opening
            second.first -> DualPart.Simultaneous
            else -> DualPart.Within
        }
}

/** A block's place in a stacked [DualPair], for 5.4's "connecting rule and a 'simultaneous' marker". */
internal enum class DualPart {
    /** The first speech's name, where the rule begins. */
    Opening,

    /** The second speech's name, which carries the marker. */
    Simultaneous,

    /** Any other line of either speech; the rule runs beside it. */
    Within,
}

/**
 * Every dual pair in a screenplay's [blocks], in order.
 *
 * A `^` with no speech before it -- a marked name straight after action -- pairs with nothing, and
 * is laid out as the ordinary speech it is.
 */
internal fun dualPairsOf(blocks: List<EditorBlock>): List<DualPair> {
    val pairs = mutableListOf<DualPair>()

    blocks.forEachIndexed { index, editorBlock ->
        if (isSimultaneous(editorBlock.block)) {
            val name = nameAbove(blocks, index)
            if (name != null) {
                var end = index
                while (end + 1 < blocks.size && blocks[end + 1].isSpoken()) end++
                pairs += DualPair(first = name until index, second = index..end)
            }
        }
    }
    return pairs
}

/** The unmarked name whose speech ends just above [index], or null if there is no such speech. */
private fun nameAbove(
    blocks: List<EditorBlock>,
    index: Int,
): Int? {
    var at = index - 1
    while (at >= 0 && blocks[at].isSpoken()) at--

    return at.takeIf {
        it >= 0 && it < index - 1 && blocks[it].block.role == BlockRole.CHARACTER &&
            !isSimultaneous(blocks[it].block)
    }
}

private fun EditorBlock.isSpoken(): Boolean = block.role == BlockRole.DIALOGUE || block.role == BlockRole.PARENTHETICAL

/**
 * The editor's list rows over its blocks: one row to a block, except that each pair in [folded] is
 * one row holding both its speeches.
 *
 * The list shows rows, and everything else in the editor speaks in blocks -- the caret, the outline,
 * a restored session's scroll position. This is the one place the two are translated. For a document
 * with no folded pair, which is every Markdown document, a row is a block.
 */
@Immutable
internal data class EditorRows(
    private val blockCount: Int,
    private val folded: List<DualPair>,
) {
    val size: Int get() = blockCount - folded.sumOf { it.blocks.last - it.blocks.first }

    /** The folded pair row [row] shows, or null if it shows one block. */
    fun pairAt(row: Int): DualPair? = blockAt(row).let { first -> folded.firstOrNull { it.blocks.first == first } }

    /** The first block row [row] shows. */
    fun blockAt(row: Int): Int {
        var shift = 0
        for (pair in folded) {
            val pairRow = pair.blocks.first - shift
            if (row <= pairRow) return if (row == pairRow) pair.blocks.first else row + shift
            shift += pair.blocks.last - pair.blocks.first
        }
        return row + shift
    }

    /** The row that shows block [index]. */
    fun rowOf(index: Int): Int {
        var shift = 0
        for (pair in folded) {
            when {
                index < pair.blocks.first -> break
                index <= pair.blocks.last -> return pair.blocks.first - shift
                else -> shift += pair.blocks.last - pair.blocks.first
            }
        }
        return index - shift
    }
}

/**
 * Scrolls to block [index] of [state]'s document, [offset] pixels into the row that shows it.
 *
 * What a caller outside the editor uses in place of `scrollToItem`, which speaks in rows: a dual
 * pair set side by side is one row for several blocks, so above one the two numbers differ.
 */
suspend fun LazyListState.scrollToBlock(
    state: EditorState,
    index: Int,
    offset: Int = 0,
) = scrollToItem(state.rows.rowOf(index), offset)

/** The first block in the first row on screen: `firstVisibleItemIndex`, in blocks. */
fun LazyListState.firstVisibleBlock(state: EditorState): Int = state.rows.blockAt(firstVisibleItemIndex)
