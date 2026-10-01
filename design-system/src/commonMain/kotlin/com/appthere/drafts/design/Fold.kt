package com.appthere.drafts.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which way a hinge runs across a window.
 *
 * Named for the line, not for what it separates: [Vertical] is a hinge that runs top to bottom and
 * therefore divides the window into a left and a right. That is the one `appthere-drafts.md` 6 is
 * about -- it is the one that cuts across a line of text.
 */
enum class FoldAxis {
    /** A hinge running top to bottom. It divides the window left from right. */
    Vertical,

    /** A hinge running left to right. It divides the window top from bottom. */
    Horizontal,
}

/**
 * Where the hinge is, as the platform reports it.
 *
 * [start] and [end] are measured along the axis the hinge cuts across: from the window's left edge
 * for a [FoldAxis.Vertical] hinge, from its top for a horizontal one. They are equal on a device
 * whose fold is a crease rather than a gap, which is most of them -- the Pixel Fold's inner display
 * reports a zero-width hinge at a fixed position.
 *
 * [isSeparating] is the platform's own answer to "are these two logical halves", and it is the
 * question 6 cares about rather than "is there a fold". A foldable lying flat reports its hinge and
 * sets this false: the display is one continuous surface, and moving the text off half of it would
 * cost the reader half their screen for a crease they can see through. A fold held in book posture
 * sets it true, and that is 6's "book-posture fold".
 */
@Immutable
data class Fold(
    val axis: FoldAxis,
    val start: Dp,
    val end: Dp,
    val isSeparating: Boolean,
)

/**
 * How much of a window's width to leave empty so that the content column clears a hinge.
 *
 * Start and end rather than left and right: this is handed to a `padding` modifier, which resolves
 * them against the layout direction. A hinge, unlike a margin, is in a physical place -- so the
 * caller converts, and this type is the layout's vocabulary rather than the device's.
 */
@Immutable
data class FoldClearance(
    val start: Dp,
    val end: Dp,
) {
    /** Nothing to clear: no fold, a fold that does not separate, or one that misses the measure. */
    val isEmpty: Boolean get() = start == 0.dp && end == 0.dp

    companion object {
        val None = FoldClearance(0.dp, 0.dp)
    }
}

/**
 * The space to leave so the content column sits on one side of a separating fold, per 6.
 *
 * "Foldables: use Jetpack WindowManager's `FoldingFeature` to avoid rendering text across a hinge.
 * On a book-posture fold, place the content column entirely on one side or split into two panes at
 * the hinge -- never let the fold bisect the measure."
 *
 * Of 6's two options this takes the first. The second needs a second thing to show in the second
 * pane, and the only candidate 6 offers is the outline, which does not exist yet (see
 * `divergences.md`, 10.1). Showing the same document twice would not be two panes; it would be two
 * copies of one.
 *
 * The side chosen is the wider one, so the measure keeps as much of its 5.3 width as the device
 * allows. On the Pixel Fold's inner display the hinge sits a little left of centre, so that is the
 * right-hand half -- 430dp of the 841dp window rather than 411. The column is narrower than it
 * would be across the whole display, which is the trade 6 is asking for: a shorter line a reader
 * can follow, rather than a longer one broken in the middle.
 *
 * A horizontal hinge gets nothing. It separates the window top from bottom, which does not bisect
 * the measure -- a line of text crosses a vertical hinge along its length and a horizontal one at a
 * point -- and the remedy that would apply, confining a scrolling document to half the height of an
 * already short window, costs more than it buys. Recorded in `divergences.md`.
 */
fun Fold?.clearanceWithin(windowWidth: Dp): FoldClearance {
    // A fold that does not separate, and a horizontal one, are both "nothing to clear" rather than
    // special cases of the arithmetic below -- so they are the same branch as having no fold.
    val fold =
        this?.takeIf { it.isSeparating && it.axis == FoldAxis.Vertical }
            ?: return FoldClearance.None

    val beforeHinge = fold.start
    val afterHinge = windowWidth - fold.end

    return if (afterHinge > beforeHinge) {
        FoldClearance(start = fold.end.coerceIn(0.dp, windowWidth), end = 0.dp)
    } else {
        FoldClearance(start = 0.dp, end = (windowWidth - fold.start).coerceIn(0.dp, windowWidth))
    }
}
