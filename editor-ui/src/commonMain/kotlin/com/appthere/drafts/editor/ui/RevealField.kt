package com.appthere.drafts.editor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.editor.engine.BlockId

/**
 * Reveal: the raw source, markup visible, in the block's own type.
 *
 * The field is *controlled*: its value is rebuilt from the session and the caret on every
 * recomposition rather than held in local state. Local state was the obvious first shape and it is
 * wrong -- the block object changes identity on every keystroke, so a `remember` keyed on it resets
 * the value and sends the caret back to offset 0 mid-word. Deriving it means there is one copy of
 * the truth and nothing to fall out of step with it.
 *
 * The callbacks follow the same rule, and read the block from the session *when the edit arrives*
 * rather than closing over [source] and the span from the last composition. Keys can arrive faster
 * than the editor composes -- two inside one frame on a slow device is ordinary typing -- and an
 * edit applied against the block as it was a frame ago lands in the wrong place: "ab" typed into an
 * empty block came out "aba", and Backspace straight after a key merged the paragraph instead of
 * deleting the key (`FastTypingTest`).
 */
@Composable
internal fun RevealField(
    state: EditorState,
    id: BlockId,
    source: String,
    style: TextStyle,
    softWrapped: Boolean,
    modifier: Modifier = Modifier,
) {
    val caret = state.caret ?: return
    val requester = remember { FocusRequester() }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicTextField(
        value = TextFieldValue(source, TextRange(caret.offset.coerceIn(0, source.length))),
        onValueChange = { edited ->
            val block = state.blockOf(id)
            val span = block?.source
            if (span != null && edited.text != state.sourceOf(block)) {
                state.replace(span, edited.text, edited.selection.start)
            } else {
                state.place(caret.copy(offset = edited.selection.start))
            }
        },
        onTextLayout = { layout.value = it },
        textStyle = style,
        visualTransformation =
            if (softWrapped) ReflowNewlines(LocalPalette.current.muted) else VisualTransformation.None,
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onPreviewKeyEvent { event ->
                    val now = state.caret?.takeIf { it.block == id } ?: caret
                    val length = state.blockOf(id)?.let(state::sourceOf)?.length ?: source.length
                    state.handle(event, now.offset, length, layout.value)
                },
    )

    // The field replaces the preview only once focus has already moved here, so it has to claim the
    // caret itself. Without this a click selects the block and then types nowhere.
    LaunchedEffect(id) { requester.requestFocus() }
}

/**
 * The keys that reach outside the block, which the field cannot handle for itself.
 *
 * 4.3 names them: "Up-arrow on the first line of a block, down-arrow on the last, Home/End, and
 * backspace at offset 0 (which merges blocks) all require the engine to move focus and place the
 * caret at the correct offset in the neighbour."
 *
 * Every branch returns false unless it actually did something, which hands the key straight back to
 * the field. That is what leaves ordinary movement *inside* a block completely untouched -- the
 * common case by a wide margin, and the one where interference would be most obvious.
 *
 * Arriving in a neighbour puts the caret at the near edge of it: the end when coming from below,
 * the start when coming from above. Preserving the horizontal position across the jump, as a
 * single-field editor does, needs the target block's layout before it has been composed, and is
 * left for the real navigation work in Phase 4.
 */
private fun EditorState.handle(
    event: KeyEvent,
    offset: Int,
    length: Int,
    layout: TextLayoutResult?,
): Boolean =
    when {
        event.type != KeyEventType.KeyDown -> {
            false
        }

        event.key == Key.Enter -> {
            split()
            true
        }

        // Only at offset 0, and only when there is something above: everywhere else, and at the top
        // of the document, Backspace is the field's own.
        event.key == Key.Backspace -> {
            offset == 0 && mergeWithPrevious()
        }

        else -> {
            moveAcrossBoundary(event, offset, length, layout)
        }
    }

/**
 * The four arrow keys, each of which leaves the block only from the edge it points at.
 *
 * Up and Down need the layout, because "the first line" of a block is a question about how the text
 * wrapped, not about offsets. A block that has not been laid out yet is treated as a single line,
 * which is the right guess: it is the state a one-line block is in on its first frame.
 */
private fun EditorState.moveAcrossBoundary(
    event: KeyEvent,
    offset: Int,
    length: Int,
    layout: TextLayoutResult?,
): Boolean {
    val line = layout?.getLineForOffset(offset)
    val onFirstLine = line == null || line == 0
    val onLastLine = layout == null || line == layout.lineCount - 1

    return when (event.key) {
        Key.DirectionLeft -> offset == 0 && moveToPrevious()
        Key.DirectionRight -> offset == length && moveToNext()
        Key.DirectionUp -> onFirstLine && moveToPrevious()
        Key.DirectionDown -> onLastLine && moveToNext()
        else -> false
    }
}
