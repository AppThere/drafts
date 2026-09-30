package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.DocumentState

/**
 * 8.4's state, in the window chrome.
 *
 * "Surface the state in the window chrome -- quietly, as a dot or a short label, not a dialog.
 * Dialogs only on attempted write." So: a dot, a word, and no interruption. Whatever the document's
 * state, the reader can keep typing.
 *
 * The dot is filled or hollow, not merely a different colour. A reader who cannot distinguish the
 * two colours still sees the difference between a document that is saved and one that is not, which
 * is what 10.2's "never colour alone" is for -- and `clean` versus `dirty` is the distinction that
 * matters most often.
 *
 * `clean` shows no label. A document that matches its file has nothing to say, and a permanent
 * "Saved" is the kind of chrome that stops being read after a day.
 */
@Composable
fun DocumentStateBadge(
    state: DocumentState,
    modifier: Modifier = Modifier,
    onSave: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val label = labelOf(state)
    val tint = if (needsAttention(state)) palette.accent else palette.muted

    // Saving is a keyboard shortcut and a phone has no keyboard, so on a touch device there was no
    // way to save at all. The indicator becomes the way: 12 says it "is the only persistent
    // chrome", so a second button beside it would be the wrong answer to that -- and the thing
    // that says there is something to save is the obvious thing to press about it.
    //
    // Only when there is. A clean document has nothing to do, and a control that does nothing is
    // worse than no control.
    val save = onSave.takeIf { state != DocumentState.Clean }

    Row(
        modifier
            // 10.2: "Touch targets >= 48dp." A dot is four.
            .then(if (save == null) Modifier else Modifier.sizeIn(minWidth = target, minHeight = target))
            .then(
                if (save == null) {
                    Modifier
                } else {
                    // The label is what a screen reader offers instead of "double-tap to
                    // activate", which says what the gesture is and not what it does.
                    Modifier.clickable(onClickLabel = Strings.SAVE_NOW, onClick = save)
                },
            )
            // One announcement for the whole badge, not one for the dot and another for the label.
            // The dot carries no text of its own, so without this the state would reach a screen
            // reader as a bare word with nothing saying what it describes.
            //
            // It does not clear the click above it: `clearAndSetSemantics` clears what is inside
            // it, and `clickable` is outside. Checked by removing the action and watching a screen
            // reader still activate it, rather than assumed either way.
            .clearAndSetSemantics { contentDescription = "${Strings.DOCUMENT_STATE}, $label" },
        horizontalArrangement = Arrangement.spacedBy(dotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(filled = state != DocumentState.Clean, tint = tint)

        if (state != DocumentState.Clean) {
            BasicText(text = label, style = TextStyle(color = tint, fontSize = labelSize))
        }
    }
}

/**
 * Hollow when there is nothing to report, filled when there is.
 *
 * Filled does not mean "unsaved" specifically -- a read-only document with no edits is safe on disk
 * and still filled, because `readOnly` is something the reader needs to know. The label says which
 * of the four it is; the dot only says whether to read the label.
 *
 * Sized in dp and not in em: this is chrome, and it should not grow with the prose.
 */
@Composable
private fun Dot(
    filled: Boolean,
    tint: Color,
) {
    Spacer(
        Modifier
            .size(dotSize)
            .background(if (filled) tint else Color.Transparent, CircleShape)
            .border(hairline, tint, CircleShape),
    )
}

/**
 * The short label for each state.
 *
 * Exhaustive `when` on purpose: a sixth state added to 8.4 should stop compiling here rather than
 * quietly display as whatever the `else` branch said.
 */
private fun labelOf(state: DocumentState): String =
    when (state) {
        DocumentState.Untitled -> Strings.STATE_UNTITLED
        DocumentState.Clean -> Strings.STATE_CLEAN
        DocumentState.Dirty -> Strings.STATE_DIRTY
        DocumentState.Conflicted -> Strings.STATE_CONFLICTED
        DocumentState.Orphaned -> Strings.STATE_ORPHANED
        DocumentState.ReadOnly -> Strings.STATE_READ_ONLY
    }

/**
 * Worth the accent colour: something has happened to the file, rather than to the text.
 *
 * Not `untitled`. Nothing has happened to a file that does not exist yet; the label says the words
 * are not in one, and that is information rather than an alarm.
 */
private fun needsAttention(state: DocumentState): Boolean =
    state == DocumentState.Conflicted || state == DocumentState.Orphaned

private val dotSize = 8.dp
private val dotGap = 6.dp
private val hairline = 1.dp
private val labelSize = 12.sp

/** 10.2's minimum touch target. */
private val target = 48.dp
