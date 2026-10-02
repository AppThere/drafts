package com.appthere.drafts.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.key_ctrl
import com.appthere.drafts.i18n.resources.key_shift
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a key is called in the shortcut list.
 *
 * Two shapes, because keys come in two kinds. The letter printed on a key is the same in every
 * language this application ships in, and putting "S" through 11.1's resources would invite a
 * translator to change it into something the keyboard does not have. A key with a *name* -- Esc,
 * Backspace, Delete -- is words, and words are translated.
 */
sealed interface KeyLabel {
    /** The character printed on the key. */
    data class Letter(
        val of: String,
    ) : KeyLabel

    /** A key whose label is a word. */
    data class Named(
        val word: StringResource,
    ) : KeyLabel
}

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
    val action: StringResource,
    val key: Key,
    val label: KeyLabel,
    val primary: Boolean = false,
    val shift: Boolean = false,
) {
    constructor(
        action: StringResource,
        key: Key,
        letter: String,
        primary: Boolean = false,
        shift: Boolean = false,
    ) : this(action, key, KeyLabel.Letter(letter), primary, shift)

    fun matches(event: KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown &&
            event.key == key &&
            (event.isCtrlPressed || event.isMetaPressed) == primary &&
            event.isShiftPressed == shift

    /**
     * The keys as the list shows them: "Ctrl+Shift+S".
     *
     * Joined with a plus rather than through a resource, which is the one place 11.1's "no string
     * concatenation" does not apply: a chord is a notation, not a sentence, the number of parts
     * varies, and the plus is the same notation in every language. The *names* being joined are
     * resources, which is the part that has to be translatable.
     */
    @Composable
    fun keys(): String =
        listOfNotNull(
            stringResource(Res.string.key_ctrl).takeIf { primary },
            stringResource(Res.string.key_shift).takeIf { shift },
            when (val of = label) {
                is KeyLabel.Letter -> of.of
                is KeyLabel.Named -> stringResource(of.word)
            },
        ).joinToString("+")
}
