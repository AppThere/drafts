package com.appthere.drafts.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appthere.drafts.design.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 5.5's settings for one window, and whether the reader's last change to them was kept.
 *
 * A change applies at once and is written in the background. Whether it was kept is decided by the
 * *most recent* write: a stepper pressed three times is three writes in flight, and only the last
 * says what is on disk now. Each write takes a ticket so an earlier one finishing late cannot
 * overwrite a later answer.
 */
@Stable
internal class WindowSettings(
    initial: ReaderSettings,
) {
    var current: ReaderSettings by mutableStateOf(initial)
        private set

    /** True when the latest change could not be written: it applies now, and will be gone next time. */
    var unsaved: Boolean by mutableStateOf(false)
        private set

    private var ticket = 0

    fun change(
        changed: ReaderSettings,
        scope: CoroutineScope,
        keep: suspend (ReaderSettings) -> Boolean,
    ) {
        current = changed
        val mine = ++ticket
        scope.launch {
            val kept = keep(changed)
            if (mine == ticket) unsaved = !kept
        }
    }
}
