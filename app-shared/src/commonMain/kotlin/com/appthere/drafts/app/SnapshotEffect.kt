package com.appthere.drafts.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalWindowInfo
import com.appthere.drafts.platform.files.SnapshotTrigger
import kotlinx.coroutines.delay

/**
 * Runs 8.1's triggers for as long as the document is on screen.
 *
 * Three of the five are wired here. "Window close, before teardown" belongs to whoever owns the
 * window, because it has to happen before the composition is gone; the desktop entry point does it.
 * "Explicit or implicit navigation away from the document" has nothing to hook yet -- there is no
 * navigation -- and will arrive with it rather than being faked now.
 *
 * The timed pair sleeps until the schedule's own deadline instead of polling. A loop waking every
 * second to ask whether three seconds have passed would be a wake-up per second per open document,
 * which on a phone is the difference between an editor and a battery complaint.
 */
@Composable
fun SnapshotEffect(
    keeper: SnapshotKeeper,
    revision: Int,
    scrollOffset: () -> Int,
    now: () -> Long = Elapsed::millis,
) {
    val focused = LocalWindowInfo.current.isWindowFocused

    // The effects below outlive the recomposition that started them, so reading the lambda through
    // `rememberUpdatedState` is what stops a running snapshot loop capturing a scroll reader that
    // belongs to a frame that has already gone.
    val currentScroll by rememberUpdatedState(scrollOffset)

    // Injected so a test can drive it from the same virtual clock that `delay` obeys. With a real
    // monotonic source and a test clock the two disagree, the deadline never arrives, and the loop
    // spins -- which is a property of the mixture rather than of the code being tested.
    val currentTime by rememberUpdatedState(now)

    LaunchedEffect(keeper, revision) {
        // Revision zero is the document as opened. Recording that as an edit would have every
        // document snapshotted three seconds after opening, whether or not anyone touched it.
        if (revision == 0) return@LaunchedEffect

        keeper.edited(currentTime())

        // Re-entered on the next keystroke, which cancels this and starts again -- so the idle
        // deadline follows the typing without anything having to cancel it explicitly.
        while (keeper.hasUnsavedEdits()) {
            val due = keeper.nextDueAt() ?: break
            val wait = due - currentTime()

            // A deadline already in the past means the last attempt did not write -- a full disk,
            // a directory that went away. Retrying immediately would spin; giving up would leave
            // the work uncaptured, which is the one outcome 8.1 exists to prevent.
            delay(if (wait > 0) wait else RETRY_AFTER_MILLIS)
            keeper.snapshotIfDue(currentTime(), currentScroll())
        }
    }

    LaunchedEffect(keeper, focused) {
        // 8.1's "Application or window loses focus (backgrounded, blurred, scene deactivated)".
        // On mobile this may be the last moment before the process is killed outright.
        if (!focused) keeper.snapshotOn(SnapshotTrigger.FocusLost, currentScroll())
    }
}

/** How long to wait before trying a snapshot again after one failed to write. */
private const val RETRY_AFTER_MILLIS = 5_000L
