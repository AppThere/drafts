package com.appthere.drafts.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.LocalReaderSettings
import com.appthere.drafts.design.LocalWindowSize
import com.appthere.drafts.design.ProseRole
import com.appthere.drafts.design.WidthClass
import com.appthere.drafts.design.proseStyleOf

/**
 * Draws the editor's list rows. [EditorRows] says which blocks a row shows; this lays them out.
 *
 * A row is one block in the column, or a dual pair folded into one row: side by side where the
 * window has room, and its blocks one under another where it does not (5.4).
 */
internal class DocumentRows(
    private val state: EditorState,
    private val layer: SelectionLayer,
    private val columnWidth: Dp,
    /**
     * The fade between the block the caret left and the one it is in, which is also where the
     * caret is: read from here rather than from [state] by each row, which would compose every row
     * on screen again whenever the caret moved -- on every keystroke.
     */
    private val fade: RevealFade,
) {
    // Read once for the editor rather than once a row: each is a state read, and a row on screen
    // is composed again on every keystroke that shifts it.
    private val blocks = state.blocks
    private val rows = state.rows
    private val pairs = state.dualPairs

    /** List row [row]. */
    @Composable
    fun ListRow(
        row: Int,
        modifier: Modifier = Modifier,
    ) {
        val pair = rows.pairAt(row)
        val index = rows.blockAt(row)

        when {
            pair == null -> {
                InColumn(index, modifier)
            }

            LocalWindowSize.current.width == WidthClass.Compact -> {
                Column(modifier) { pair.blocks.forEach { InColumn(it) } }
            }

            else -> {
                val last = proseStyleOf(state.proseRoleOf(blocks[pair.blocks.last]))
                DualRow(
                    pair = pair,
                    columnWidth = columnWidth,
                    spaceBefore = collapsedSpace(roleAbove(index), state.proseRoleOf(blocks[index])),
                    spaceAfter = if (pair.blocks.last == blocks.lastIndex) last.spaceAfter else 0.dp,
                    block = { at, half -> InHalf(at, half) },
                    modifier = modifier,
                )
            }
        }
    }

    /** Block [index] in the column: a row of its own, or one line of a stacked dual pair. */
    @Composable
    private fun InColumn(
        index: Int,
        modifier: Modifier = Modifier,
    ) {
        // Everything the row needs that a shift does not change. A block that only moved gets back
        // the identical `RowContent`, so the row is skipped rather than composed again -- measured
        // at one row composed per keystroke instead of every visible one.
        val content = state.rowContentOf(blocks[index], LocalPalette.current.muted)
        val before = collapsedSpace(roleAbove(index), content.role)
        val after = if (index == blocks.lastIndex) proseStyleOf(content.role).spaceAfter else 0.dp

        Block(
            index = index,
            content = content,
            geometry = RowGeometry.of(columnWidth, content.role, before, after),
            dual = pairs.firstOrNull { index in it.blocks }?.partOf(index),
            modifier = modifier,
        )
    }

    /** Block [index] in one column of a dual pair set side by side, [half] wide. */
    @Composable
    private fun InHalf(
        index: Int,
        half: Dp,
    ) {
        val content = state.rowContentOf(blocks[index], LocalPalette.current.muted)
        Block(index, content, RowGeometry.inHalf(half, content.role), dual = null)
    }

    @Composable
    private fun Block(
        index: Int,
        content: RowContent,
        geometry: RowGeometry,
        dual: DualPart?,
        modifier: Modifier = Modifier,
    ) {
        val id = blocks[index].id
        BlockRow(
            state = state,
            layer = layer,
            id = id,
            content = content,
            emphasis = emphasisOf(id, fade.focused, LocalReaderSettings.current.focusMode),
            fade = fade.of(id),
            geometry = geometry,
            dual = dual,
            modifier = modifier,
        )
    }

    private fun roleAbove(index: Int): ProseRole? = blocks.getOrNull(index - 1)?.let(state::proseRoleOf)
}

/**
 * The gap above a block: the larger of its own space-before and the space-after of the block above.
 *
 * Collapsing, as CSS does it. The two values in 5.2's table are a pair of margins, and margins that
 * meet do not stack -- a heading after a paragraph gets the heading's 1.5em, not 1.5em plus the
 * paragraph's 0.75em.
 *
 * Both are resolved to Dp before comparing, because each is em of its *own* role's size: 0.75em of
 * body is smaller than 1.5em of an H2 by more than the ratio of the two numbers suggests.
 */
@Composable
private fun collapsedSpace(
    above: ProseRole?,
    role: ProseRole,
): Dp {
    val own = proseStyleOf(role).spaceBefore
    val previous = above?.let { proseStyleOf(it).spaceAfter } ?: 0.dp

    return maxOf(own, previous)
}
