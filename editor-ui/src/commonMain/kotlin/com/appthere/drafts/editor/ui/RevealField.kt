package com.appthere.drafts.editor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.changeBetween

/**
 * Reveal: the raw source, markup visible, in the block's own type.
 *
 * The field keeps its own [TextFieldState], and while the reader types that state is the truth:
 * every edit the field makes -- a letter, Backspace, Delete, a word from the input method -- is
 * applied to its state at once, and handed to the session in the same moment by
 * [InputTransformation]. Nothing is ever applied against an older copy of the text.
 *
 * It replaces a value-based field that was rebuilt from the session on every composition, and so
 * applied the keys it handled itself to the text as it stood a frame ago. Keys arrive faster than
 * that on a slow device: Backspace straight after a letter was lost, and before the callbacks read
 * the session directly, letters doubled ("ab" typed into an empty block came out "aba";
 * `FastTypingTest`).
 *
 * The session still wins when *it* changes the block -- undo, a split or merge, a change of kind --
 * and the field is brought into line after the composition that shows it. The caret travels the
 * same way, in whichever direction it moved: from the field to the session as the reader moves it,
 * from the session to the field when something else places it.
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
    val field = remember(id) { TextFieldState(source, TextRange(caret.offset.coerceIn(0, source.length))) }

    // The caret offset the field and the session last agreed on, so that a caret the session moved
    // can be told apart from one the field moved and the session has not heard about yet.
    val agreed = remember(id) { AgreedCaret(caret.offset) }

    SideEffect {
        val placed = caret.offset.coerceIn(0, source.length)
        if (field.text.toString() != source) {
            field.edit {
                replace(0, length, source)
                selection = TextRange(placed)
            }
            agreed.offset = placed
        } else if (caret.offset != agreed.offset && field.selection.start != placed) {
            field.edit { selection = TextRange(placed) }
            agreed.offset = placed
        }
    }

    // The reader moving the caret within the block -- arrows, a click, a drag -- reaches the
    // session here. Text edits carry their own caret, through the input transformation below.
    LaunchedEffect(field) {
        snapshotFlow { field.selection.start }.collect { at -> agreed.offset = state.caretTo(id, at) ?: at }
    }

    BasicTextField(
        state = field,
        inputTransformation =
            InputTransformation {
                val block = state.blockOf(id)
                val span = block?.source ?: return@InputTransformation
                val edited = toString()
                if (edited != state.sourceOf(block)) {
                    state.replace(span, edited, selection.start)
                    agreed.offset = selection.start
                }
            },
        onTextLayout = { result -> layout.value = result() },
        textStyle = style,
        outputTransformation = if (softWrapped) ReflowNewlines(LocalPalette.current.muted) else null,
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onPreviewKeyEvent { event ->
                    // The field's own caret, which is never behind: the session may not yet have
                    // heard about a move the reader made this frame.
                    val at = field.selection.start
                    state.caretTo(id, at)
                    state.handle(event, at, field.text.length, layout.value)
                },
    )

    // The field replaces the preview only once focus has already moved here, so it has to claim the
    // caret itself. Without this a click selects the block and then types nowhere.
    LaunchedEffect(id) { requester.requestFocus() }
}

/** A caret offset, held across compositions without being state that recomposes anything. */
private class AgreedCaret(
    var offset: Int,
)

/**
 * Moves the session's caret to [offset] in [block], if the caret is in that block and somewhere
 * else in it. Returns the offset it is now at, or null if the caret is in another block.
 */
private fun EditorState.caretTo(
    block: BlockId,
    offset: Int,
): Int? {
    val current = caret?.takeIf { it.block == block } ?: return null
    if (current.offset != offset) place(current.copy(offset = offset))
    return offset
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

/**
 * Replaces the focused block's source with what the field now holds.
 *
 * The field reports its whole contents, but what is *recorded* is narrowed to the run that
 * actually changed. Recording the whole block would make every keystroke a replacement of
 * everything, which no amount of care in [UndoHistory] can coalesce -- undo would step back one
 * character at a time. See [changeBetween].
 */
fun EditorState.replace(
    span: SourceSpan,
    replacement: String,
    offset: Int,
) {
    val block = caret?.block ?: return
    val existing = text.substring(span.start.value, span.endExclusive.value)
    val change = changeBetween(existing, replacement) ?: return

    edit(SourceSpan.of(span.start.value + change.start, span.start.value + change.endExclusive), change.replacement)
    place(Caret(block, offset))
}
