package com.appthere.drafts.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.key_backspace
import com.appthere.drafts.i18n.resources.key_delete
import com.appthere.drafts.i18n.resources.shortcut_copy
import com.appthere.drafts.i18n.resources.shortcut_cut
import com.appthere.drafts.i18n.resources.shortcut_delete_selection
import com.appthere.drafts.i18n.resources.shortcut_paste
import com.appthere.drafts.i18n.resources.shortcut_redo
import com.appthere.drafts.i18n.resources.shortcut_select_all
import com.appthere.drafts.i18n.resources.shortcut_undo

/**
 * The editor's own shortcuts, which act on the document rather than on a field.
 *
 * Ctrl+Shift+Z and Ctrl+Y both redo: the first is what the rest of the document editor world uses
 * and the second is what Windows users reach for, and neither is worth making someone look up.
 * Paste is here to be listed and not to be handled: the field the caret is in pastes, as every text
 * field does.
 */
object EditorShortcuts {
    val SelectAll = Shortcut(Res.string.shortcut_select_all, Key.A, "A", primary = true)
    val Copy = Shortcut(Res.string.shortcut_copy, Key.C, "C", primary = true)
    val Cut = Shortcut(Res.string.shortcut_cut, Key.X, "X", primary = true)
    val Paste = Shortcut(Res.string.shortcut_paste, Key.V, "V", primary = true)
    val Undo = Shortcut(Res.string.shortcut_undo, Key.Z, "Z", primary = true)
    val Redo = Shortcut(Res.string.shortcut_redo, Key.Z, "Z", primary = true, shift = true)
    val RedoAlso = Shortcut(Res.string.shortcut_redo, Key.Y, "Y", primary = true)
    val DeleteBackward =
        Shortcut(Res.string.shortcut_delete_selection, Key.Backspace, KeyLabel.Named(Res.string.key_backspace))
    val DeleteForward =
        Shortcut(Res.string.shortcut_delete_selection, Key.Delete, KeyLabel.Named(Res.string.key_delete))

    /** In the order the list shows them. */
    val all: List<Shortcut> = listOf(SelectAll, Copy, Cut, Paste, Undo, Redo, RedoAlso, DeleteBackward, DeleteForward)
}

/**
 * The editor's shortcuts, handled.
 *
 * Public, and called from the composition root rather than from here. Key events travel from the
 * focus owner *outwards*, so a handler on this list only ever sees events on their way to a block
 * -- which means none at all until the reader has clicked into one. The root holds focus when a
 * document opens and stays an ancestor of whatever takes it next, so that is the one place a
 * handler sees every keystroke.
 *
 * 4.4: "implement copy/cut/delete against the IR rather than against any text field". When a
 * selection spans blocks there is no field to ask, so these go to the engine, which answers in
 * source text. Deleting a selection only applies when there is one: with nothing selected those
 * keys belong to whatever field has focus, which is why [deleteSelection] answering false lets the
 * key through.
 */
fun EditorState.handleShortcut(
    event: KeyEvent,
    clipboard: ClipboardManager,
): Boolean {
    val shortcut = EditorShortcuts.all.firstOrNull { it.matches(event) }
    return when (shortcut) {
        EditorShortcuts.SelectAll -> selectAll().let { true }
        EditorShortcuts.Copy -> copyTo(clipboard)
        EditorShortcuts.Cut -> copyTo(clipboard) && deleteSelection()
        EditorShortcuts.Undo -> undo()
        EditorShortcuts.Redo, EditorShortcuts.RedoAlso -> redo()
        EditorShortcuts.DeleteBackward, EditorShortcuts.DeleteForward -> deleteSelection()
        else -> false
    }
}

/**
 * Puts the selection on the clipboard.
 *
 * `ClipboardManager` is deprecated in favour of `Clipboard`, and the warning is left standing on
 * purpose. The replacement exchanges `ClipEntry`, which on desktop wraps a platform-native object
 * and cannot be built from common code -- migrating means a platform abstraction, which is what
 * `:platform-intents` is for and is not a Phase 2 spike's work. The deprecated call takes an
 * `AnnotatedString` and works everywhere this runs today.
 */
private fun EditorState.copyTo(clipboard: ClipboardManager): Boolean {
    val selected = selectedText()
    if (selected.isEmpty()) return false

    clipboard.setText(AnnotatedString(selected))
    return true
}
