package com.appthere.drafts.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.appthere.drafts.editor.ui.Shortcut
import com.appthere.drafts.i18n.Strings

// Every key a document window answers, in one place.
//
// 10.2 asks for "the full shortcut map" to be documented and eventually remappable
// (`divergences.md`), and a map scattered through the composables that handle the keys is one
// nobody can read off. The window's handlers match against these tables, and the shortcut list
// (`ShortcutList.kt`) shows the same tables, so the two cannot disagree.

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

/**
 * The shortcuts a document window answers itself, rather than the editor inside it.
 *
 * What each one *does* stays with the handler that answers it; this is what they are called and
 * which keys they are, for the handlers to match and the shortcut list to show.
 */
internal object WindowShortcuts {
    /** 8.2's explicit save, on the shortcut every editor uses for it. */
    val Save = Shortcut(Strings.SHORTCUT_SAVE, Key.S, "S", primary = true)

    /** 7.4's *Save As*, on the shortcut every editor uses for it. */
    val SaveAs = Shortcut(Strings.SHORTCUT_SAVE_AS, Key.S, "S", primary = true, shift = true)

    /** 5.5's settings, on the shortcut every editor uses for them. */
    val ReaderControls = Shortcut(Strings.SHORTCUT_READER_CONTROLS, Key.Comma, ",", primary = true)

    /** This list -- the key most applications that have one use for it. */
    val KeyboardShortcuts = Shortcut(Strings.SHORTCUT_LIST, Key.Slash, "/", primary = true)

    /** Escape, which cancels whatever is being asked and closes whatever is open. */
    val Dismiss = Shortcut(Strings.SHORTCUT_CLOSE, Key.Escape, Strings.KEY_ESCAPE)

    /** In the order the list shows them. */
    val all: List<Shortcut> = listOf(Save, SaveAs, ReaderControls, KeyboardShortcuts, Dismiss)
}
