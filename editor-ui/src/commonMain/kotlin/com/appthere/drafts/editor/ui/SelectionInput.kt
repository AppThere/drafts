package com.appthere.drafts.editor.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChange

/**
 * Tracks a selection drag across the whole editor.
 *
 * Written as a pointer loop rather than `detectDragGestures` because of who else wants the gesture.
 * The `LazyColumn`'s own scrolling sits *inside* this modifier, so on the main pass it sees the
 * drag first and turns it into a scroll -- the selection would never begin. Watching the initial
 * pass gets there first, and consuming the movement once a drag is genuinely under way is what
 * stops the list scrolling out from under it.
 *
 * A press is deliberately not consumed. A click that never moves has to reach the block underneath,
 * which is how a block takes focus; only movement is taken.
 *
 * Only a mouse drag selects. A finger dragging down a document means scroll, and taking that
 * movement leaves a touch reader with a document they cannot move through. Touch gets taps: the
 * press is reported only once the finger has lifted without travelling, because reporting it on
 * the way down would drop the caret wherever every scroll happened to begin. Selecting across
 * blocks by touch is 4.4's drag handles, and is not a drag of the document at all.
 */
internal suspend fun PointerInputScope.trackSelectionDrag(
    onPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        if (down.type == PointerType.Mouse) {
            onPress(down.position)
            trackMouseDrag(down, onDrag)
            onRelease()
        } else if (liftsWithoutTravelling(down, viewConfiguration.touchSlop)) {
            onPress(down.position)
            onRelease()
        }
    }
}

private suspend fun AwaitPointerEventScope.trackMouseDrag(
    down: PointerInputChange,
    onDrag: (Offset) -> Unit,
) {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) return

        if (change.positionChange() != Offset.Zero) {
            onDrag(change.position)
            change.consume()
        }
    }
}

/**
 * Whether a touch is a tap: it lifts before moving further than [slop] from where it went down.
 *
 * Nothing is consumed either way. A touch that travels is the list's to scroll, and the one that
 * does not is the block's to take focus from.
 */
private suspend fun AwaitPointerEventScope.liftsWithoutTravelling(
    down: PointerInputChange,
    slop: Float,
): Boolean {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        val travelled = change == null || (change.position - down.position).getDistance() > slop
        if (travelled || !change.pressed) return !travelled
    }
}
