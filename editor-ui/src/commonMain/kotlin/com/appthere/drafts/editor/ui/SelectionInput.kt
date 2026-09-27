package com.appthere.drafts.editor.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
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
 */
internal suspend fun PointerInputScope.trackSelectionDrag(
    onPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPress(down.position)

        var held = true
        while (held) {
            val change =
                awaitPointerEvent(PointerEventPass.Initial)
                    .changes
                    .firstOrNull { it.id == down.id }

            when {
                change == null || !change.pressed -> {
                    held = false
                }

                change.positionChange() != Offset.Zero -> {
                    onDrag(change.position)
                    change.consume()
                }
            }
        }

        onRelease()
    }
}
