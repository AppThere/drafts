package com.appthere.drafts.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

// Every key a document window answers, in one place.
//
// 10.2 asks for "the full shortcut map" to be documented and eventually remappable
// (`divergences.md`), and a map scattered through the composables that handle the keys is one
// nobody can read off. The window's handlers ask these; what each key *does* stays with them.

/**
 * A modifier going down, which 12 counts as reaching for the chrome.
 *
 * The key itself, not a shortcut using it: pressing Ctrl and thinking better of it is still the
 * reader looking for something.
 */
internal fun rouses(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        event.key in
        setOf(
            Key.CtrlLeft,
            Key.CtrlRight,
            Key.ShiftLeft,
            Key.ShiftRight,
            Key.AltLeft,
            Key.AltRight,
            Key.MetaLeft,
            Key.MetaRight,
        )

/** Escape, which cancels whatever is being asked. */
internal fun dismisses(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown && event.key == Key.Escape

/** 8.2's explicit save, on the shortcut every editor uses for it. */
internal fun saves(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.isCtrlPressed || event.isMetaPressed) &&
        !event.isShiftPressed &&
        event.key == Key.S

/** 7.4's *Save As*, on the shortcut every editor uses for it. */
internal fun savesAs(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.isCtrlPressed || event.isMetaPressed) &&
        event.isShiftPressed &&
        event.key == Key.S

/** 5.5's settings, on the shortcut every editor uses for them. */
internal fun togglesControls(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.isCtrlPressed || event.isMetaPressed) &&
        event.key == Key.Comma
