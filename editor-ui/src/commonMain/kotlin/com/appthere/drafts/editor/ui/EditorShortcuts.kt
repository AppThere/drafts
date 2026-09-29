package com.appthere.drafts.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.appthere.drafts.i18n.Strings

/**
 * The editor's own shortcuts, which act on the document rather than on a field.
 *
 * Ctrl+Shift+Z and Ctrl+Y both redo: the first is what the rest of the document editor world uses
 * and the second is what Windows users reach for, and neither is worth making someone look up.
 * Paste is here to be listed and not to be handled: the field the caret is in pastes, as every text
 * field does.
 */
object EditorShortcuts {
    val SelectAll = Shortcut(Strings.SHORTCUT_SELECT_ALL, Key.A, "A", primary = true)
    val Copy = Shortcut(Strings.SHORTCUT_COPY, Key.C, "C", primary = true)
    val Cut = Shortcut(Strings.SHORTCUT_CUT, Key.X, "X", primary = true)
    val Paste = Shortcut(Strings.SHORTCUT_PASTE, Key.V, "V", primary = true)
    val Undo = Shortcut(Strings.SHORTCUT_UNDO, Key.Z, "Z", primary = true)
    val Redo = Shortcut(Strings.SHORTCUT_REDO, Key.Z, "Z", primary = true, shift = true)
    val RedoAlso = Shortcut(Strings.SHORTCUT_REDO, Key.Y, "Y", primary = true)
    val DeleteBackward = Shortcut(Strings.SHORTCUT_DELETE_SELECTION, Key.Backspace, Strings.KEY_BACKSPACE)
    val DeleteForward = Shortcut(Strings.SHORTCUT_DELETE_SELECTION, Key.Delete, Strings.KEY_DELETE)

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
