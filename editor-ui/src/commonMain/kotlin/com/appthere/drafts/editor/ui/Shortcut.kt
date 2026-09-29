package com.appthere.drafts.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.appthere.drafts.i18n.Strings

/**
 * One keyboard shortcut: what it does, the keys for it, and whether a key event is those keys.
 *
 * `appthere-drafts.md` 10.2: "Document the full shortcut map and make it user-remappable." The
 * handlers match events against these, and the shortcut list the reader can open reads the same
 * values -- so what the list says and what the keys do cannot drift apart, and remapping, when it
 * comes, has one table to change.
 *
 * [primary] is Ctrl, or ⌘ on a Mac: every handler accepts either, as it always has. The match is
 * otherwise exact, so Ctrl+S and Ctrl+Shift+S cannot be mistaken for each other.
 */
@Immutable
data class Shortcut(
    val action: String,
    val key: Key,
    val keyName: String,
    val primary: Boolean = false,
    val shift: Boolean = false,
) {
    fun matches(event: KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown &&
            event.key == key &&
            (event.isCtrlPressed || event.isMetaPressed) == primary &&
            event.isShiftPressed == shift

    /** The keys as the list shows them: "Ctrl+Shift+S". */
    val keys: String
        get() =
            listOfNotNull(
                Strings.KEY_CTRL.takeIf {
                    primary
                },
                Strings.KEY_SHIFT.takeIf { shift },
                keyName,
            ).joinToString("+")
}
