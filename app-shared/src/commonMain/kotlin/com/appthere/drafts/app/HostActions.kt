package com.appthere.drafts.app

import androidx.compose.ui.input.key.KeyEvent
import com.appthere.drafts.editor.ui.Shortcut

/**
 * What the host adds to a window beyond its document: the keys it answers itself, and the ways to
 * another window -- a new document, or one opened from disk.
 *
 * 7.1: "One window = one document." Another document is therefore another window, which only the
 * host can make: an Android task, a desktop `Window`. So the window offers New and Open, and the
 * host does them.
 *
 * [shortcuts] are the keys the host answers outside the window -- full screen, on the desktop --
 * listed with the window's own so that 10.2's shortcut list is the whole of it.
 *
 * [newDocument] and [openDocument] are null on a host that cannot do them. The window then offers
 * neither, in the panel or on the keyboard: a control that does nothing is worse than none.
 */
class HostActions(
    val shortcuts: List<Shortcut> = emptyList(),
    val newDocument: (() -> Unit)? = null,
    val openDocument: (() -> Unit)? = null,
) {
    /** What the shortcut list shows for this host, after the window's own. */
    internal val listed: List<Shortcut>
        get() =
            listOfNotNull(
                WindowShortcuts.New.takeIf { newDocument != null },
                WindowShortcuts.Open.takeIf { openDocument != null },
            ) + shortcuts

    /** Ctrl+N and Ctrl+O, when the host can do them. True if [event] was one of them. */
    internal fun answer(event: KeyEvent): Boolean {
        val action =
            when {
                WindowShortcuts.New.matches(event) -> newDocument
                WindowShortcuts.Open.matches(event) -> openDocument
                else -> null
            } ?: return false

        action()
        return true
    }
}
