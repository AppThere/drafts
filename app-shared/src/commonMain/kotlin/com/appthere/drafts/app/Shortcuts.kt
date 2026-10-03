package com.appthere.drafts.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.appthere.drafts.editor.ui.KeyLabel
import com.appthere.drafts.editor.ui.Shortcut
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.key_escape
import com.appthere.drafts.i18n.resources.new_document
import com.appthere.drafts.i18n.resources.open_document
import com.appthere.drafts.i18n.resources.shortcut_close
import com.appthere.drafts.i18n.resources.shortcut_list
import com.appthere.drafts.i18n.resources.shortcut_outline
import com.appthere.drafts.i18n.resources.shortcut_reader_controls
import com.appthere.drafts.i18n.resources.shortcut_save
import com.appthere.drafts.i18n.resources.shortcut_save_as

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
    val Save = Shortcut(Res.string.shortcut_save, Key.S, "S", primary = true)

    /** 7.4's *Save As*, on the shortcut every editor uses for it. */
    val SaveAs = Shortcut(Res.string.shortcut_save_as, Key.S, "S", primary = true, shift = true)

    /** 5.5's settings, on the shortcut every editor uses for them. */
    val ReaderControls = Shortcut(Res.string.shortcut_reader_controls, Key.Comma, ",", primary = true)

    /** This list -- the key most applications that have one use for it. */
    val KeyboardShortcuts = Shortcut(Res.string.shortcut_list, Key.Slash, "/", primary = true)

    /** 10.1's outline, on the shortcut writing applications use for theirs. */
    val Outline = Shortcut(Res.string.shortcut_outline, Key.O, "O", primary = true, shift = true)

    /** Escape, which cancels whatever is being asked and closes whatever is open. */
    val Dismiss = Shortcut(Res.string.shortcut_close, Key.Escape, KeyLabel.Named(Res.string.key_escape))

    /** In the order the list shows them. */
    val all: List<Shortcut> = listOf(Save, SaveAs, Outline, ReaderControls, KeyboardShortcuts, Dismiss)

    /**
     * A new document in a new window (7.1), on the shortcut every application uses for it. Not in
     * [all]: only a host that can open a window answers it, so [HostActions] lists it.
     */
    val New = Shortcut(Res.string.new_document, Key.N, "N", primary = true)

    /** A document from disk in a new window. Listed by [HostActions], like [New]. */
    val Open = Shortcut(Res.string.open_document, Key.O, "O", primary = true)
}
