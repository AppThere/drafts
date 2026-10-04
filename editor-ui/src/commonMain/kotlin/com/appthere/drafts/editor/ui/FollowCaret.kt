package com.appthere.drafts.editor.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.appthere.drafts.editor.engine.BlockId

/**
 * Keeps the caret's block where the reader can see it, which is two different things depending on
 * the reader's choice.
 *
 * With 12's typewriter scrolling on, the caret's block is held at the typewriter line. Without it,
 * the document stays still -- except that a caret in a block the list has not composed is brought
 * on screen, because a block off screen has no field to hold the keyboard. See
 * [bringIntoComposition].
 */
@Composable
internal fun FollowCaret(
    state: EditorState,
    scroll: LazyListState,
    focused: BlockId?,
    typewriter: Boolean,
) {
    // 12: "**Typewriter scrolling** as an option: keep the caret at a fixed vertical position."
    //
    // Keyed on the block rather than the offset, so it settles when the caret crosses into
    // another block rather than fighting the reader for the scroll position on every keystroke
    // within one. The consequence is that it is the *block* that is held at the line, not the
    // caret inside it -- exact caret placement needs the text layout of the focused field,
    // which lives a composable below this one. A paragraph is short enough that the difference
    // is small; a forty-line one, it is not, and this is the honest first cut.
    //
    // Without it, the caret's block is still brought on screen whenever it is not: see
    // `bringIntoComposition`.
    LaunchedEffect(focused, typewriter, scroll) {
        if (focused == null) return@LaunchedEffect

        val index = state.blocks.indexOfFirst { it.id == focused }
        val viewport = scroll.layoutInfo.viewportSize.height
        if (index < 0 || viewport <= 0) return@LaunchedEffect

        if (typewriter) {
            scroll.scrollToItem(index, -(viewport * TYPEWRITER_LINE).toInt())
        } else {
            scroll.bringIntoComposition(index, viewport)
        }
    }
}

/**
 * Brings the caret's block on screen if the list has not composed it.
 *
 * Not a nicety. A block's field exists only while its row is composed, and the field is what holds
 * keyboard focus -- so a caret moved to a block below the window, by Enter on the last line or an
 * arrow off the bottom, had no field to go to, and focus left the editor with the reader's next
 * keys. On Android it went to the first thing that could take it, the save badge, and the next
 * Enter opened *Save As*.
 *
 * Arriving from above, the block comes in at the top. Arriving from below, its top comes in at
 * [ARRIVAL_LINE] rather than the top: the reader was writing at the bottom of the window and the
 * text they just wrote should stay in view above it. Once the row is composed, its field claims
 * focus and scrolls the caret's own line into view, which is finer than anything this can do from
 * outside the row. A block already on screen, even in part, is left exactly where it is.
 */
private suspend fun LazyListState.bringIntoComposition(
    index: Int,
    viewport: Int,
) {
    val shown = layoutInfo.visibleItemsInfo
    if (shown.isEmpty() || shown.any { it.index == index }) return

    if (index < shown.first().index) {
        scrollToItem(index)
    } else {
        scrollToItem(index, -(viewport * ARRIVAL_LINE).toInt())
    }
}

/** Where a block arriving from below the window puts its top, as a fraction of the viewport. */
private const val ARRIVAL_LINE = 0.66f

/**
 * Where down the window the typewriter line sits, as a fraction of the viewport.
 *
 * Above the middle rather than on it. What a writer needs to see is the sentence they have just
 * finished and the shape of the paragraph it belongs to, which is above the caret; below it there
 * is nothing yet.
 */
private const val TYPEWRITER_LINE = 0.4f
