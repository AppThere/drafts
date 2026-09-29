package com.appthere.drafts.editor.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.design.FocusMode
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.LocalReaderSettings
import com.appthere.drafts.design.Measure
import com.appthere.drafts.design.ProseRole
import com.appthere.drafts.design.proseStyleOf
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.EditorBlock

/**
 * The document, as a vertical list of blocks.
 *
 * This is the architecture `appthere-drafts.md` 4.3 chose and Phase 2 exists to test: a
 * `LazyColumn` of blocks, the focused one an editable field showing raw source, the rest formatted
 * previews. Cost is O(viewport) rather than O(document), and block boundaries double as reparse
 * boundaries.
 *
 * `key` is not a detail. `engineering-conventions.md` 4.2: a `LazyColumn` without stable keys
 * "causes focus and caret loss on structural edits -- a correctness bug, not a performance one".
 * The engine hands out ids that survive a reparse precisely so this line can use them.
 */
@Composable
fun BlockEditor(
    state: EditorState,
    modifier: Modifier = Modifier,
    scroll: LazyListState = rememberLazyListState(),
) {
    val layer = remember { SelectionLayer() }
    val focus = remember { FocusRequester() }

    // One subcomposition for the whole editor rather than one per block. `BoxWithConstraints`
    // subcomposes its content, and a `BoxWithConstraints` inside every row meant one subcomposition
    // per visible block on every keystroke. The width a block's text gets is the same arithmetic
    // for all of them, so it is done once here and passed down.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 5.3's arithmetic, not a constant: the column is 34em of the reader's own base size,
        // clamped to the window, with a gutter that is 4% of the window and never below 16dp.
        val settings = LocalReaderSettings.current
        val density = LocalDensity.current
        val column = Measure.of(maxWidth, with(density) { settings.base.toDp() }, settings.characters)

        val muted = LocalPalette.current.muted
        val focusMode = LocalReaderSettings.current.focusMode
        val typewriter = LocalReaderSettings.current.typewriterScrolling
        val focused = state.caret?.block

        // 12: "**Typewriter scrolling** as an option: keep the caret at a fixed vertical position."
        //
        // Keyed on the block rather than the offset, so it settles when the caret crosses into
        // another block rather than fighting the reader for the scroll position on every keystroke
        // within one. The consequence is that it is the *block* that is held at the line, not the
        // caret inside it -- exact caret placement needs the text layout of the focused field,
        // which lives a composable below this one. A paragraph is short enough that the difference
        // is small; a forty-line one, it is not, and this is the honest first cut.
        LaunchedEffect(focused, typewriter, scroll) {
            if (!typewriter || focused == null) return@LaunchedEffect

            val index = state.blocks.indexOfFirst { it.id == focused }
            val viewport = scroll.layoutInfo.viewportSize.height
            if (index >= 0 && viewport > 0) {
                scroll.scrollToItem(index, -(viewport * TYPEWRITER_LINE).toInt())
            }
        }

        // 10.1's structural announcements, beside the list rather than in it: the list's rows come
        // and go as they scroll, and the thing speaking must not.
        StructureAnnouncer(state)

        LazyColumn(
            state = scroll,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { layer.onContainerPositioned(it) }
                    .focusRequester(focus)
                    .focusable()
                    .pointerInput(layer) {
                        trackSelectionDrag(
                            onPress = { point ->
                                // The editor takes focus on a press so the shortcuts below have
                                // somewhere to arrive. A press that turns out to be a plain click
                                // hands focus straight on to the block's field.
                                focus.requestFocus()
                                layer.caretAt(point)?.let(state::beginSelection)
                            },
                            onDrag = { point -> layer.caretAt(point)?.let(state::extendSelection) },
                            onRelease = { state.endSelection() },
                        )
                    },
            contentPadding = PaddingValues(vertical = documentPadding, horizontal = column.gutter),
            // 5.3: "centred with generous margins on a desktop window". The column stops at its
            // measure and the leftover becomes margin on both sides rather than all on the right.
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(state.blocks, key = { _, block -> block.id.value }) { index, editorBlock ->
                // Everything the row needs that a shift does not change. A block that only moved
                // gets back the identical `RowContent`, so the row is skipped rather than composed
                // again -- measured at one row composed per keystroke instead of every visible one.
                val content = state.rowContentOf(editorBlock, muted)
                val above = state.blocks.getOrNull(index - 1)?.let { roleOf(it.block) }

                BlockRow(
                    state = state,
                    layer = layer,
                    id = editorBlock.id,
                    content = content,
                    spaceBefore = collapsedSpace(above, content.role),
                    spaceAfter = if (index == state.blocks.lastIndex) proseStyleOf(content.role).spaceAfter else 0.dp,
                    emphasis = emphasisOf(editorBlock.id, focused, focusMode),
                    columnWidth = column.contentWidth,
                )
            }
        }
    }
}

/**
 * One block, in preview or reveal.
 *
 * The two states share one [com.appthere.drafts.design.ProseStyle], which is the whole trick from
 * 4.1: "block identity is stable, inline decoration is what reveals". An `## H2` shows at H2 size
 * whether it is showing `Heading` or `## Heading`, so clicking into it does not make the line jump.
 *
 * The style is resolved here rather than carried in [RowContent] because it depends on the reader's
 * settings and the palette, which live in the composition. Those are static composition locals, so
 * reading them costs nothing per keystroke -- the subtree recomposes when the theme changes and not
 * otherwise, which leaves the row skippable for the shifts that matter.
 */
@Composable
private fun BlockRow(
    state: EditorState,
    layer: SelectionLayer,
    id: BlockId,
    content: RowContent,
    spaceBefore: Dp,
    spaceAfter: Dp,
    emphasis: RowEmphasis,
    columnWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val prose = proseStyleOf(content.role)
    val textWidth = with(LocalDensity.current) { columnWidth.roundToPx() }

    Column(
        modifier
            // 12's focus mode. Dimmed rather than hidden: a reader needs to see that there is more
            // document above and below -- how far through a chapter they are is information -- and
            // removing it would be a different feature.
            .alpha(emphasis.alpha)
            // Order matters, and got this wrong for two phases. `fillMaxWidth` fixes the width at
            // the incoming maximum -- minimum as well as maximum -- so a `widthIn` after it has
            // nothing left to constrain, and the measure silently never applied. Capping first and
            // filling second gives the column the width it asked for.
            .widthIn(max = columnWidth)
            .fillMaxWidth()
            // 5.2 gives each role its own space before and after, so a heading brings its own air
            // with it. The gap between two blocks is the *larger* of the pair, not the sum: the
            // table reads as CSS margins, and CSS collapses adjacent vertical margins. Adding them
            // gave a document with visibly more air between every pair of blocks than 5.2 asks for.
            .padding(top = spaceBefore, bottom = spaceAfter),
    ) {
        // `textWidth` is the width the text itself will be laid out under, which is what makes the
        // reserved height the right one.
        // Measured against the source *as reveal draws it*: newlines are marked rather than
        // obeyed, so the two states differ only by the markup characters -- which is the case 4.2
        // was actually written for, and a far smaller one.
        val reserved =
            reservedHeightOf(
                content.preview.text,
                if (content.softWrapped) reflowedForDisplay(content.source) else content.source,
                prose.textStyle,
                textWidth,
            )

        Box(Modifier.fillMaxWidth().heightIn(min = reserved)) {
            if (emphasis == RowEmphasis.Focused) {
                RevealField(
                    state = state,
                    id = id,
                    source = content.source,
                    style = prose.textStyle,
                    softWrapped = content.softWrapped,
                )
            } else {
                PreviewText(
                    state = state,
                    layer = layer,
                    id = id,
                    preview = content.preview,
                    style = prose.textStyle,
                    modifier = Modifier.spoken(content.spoken),
                )
            }
        }
    }
}

/**
 * The gap above a block: the larger of its own space-before and the space-after of the block above.
 *
 * Collapsing, as CSS does it. The two values in 5.2's table are a pair of margins, and margins that
 * meet do not stack -- a heading after a paragraph gets the heading's 1.5em, not 1.5em plus the
 * paragraph's 0.75em.
 *
 * Both are resolved to Dp before comparing, because each is em of its *own* role's size: 0.75em of
 * body is smaller than 1.5em of an H2 by more than the ratio of the two numbers suggests.
 */
@Composable
private fun collapsedSpace(
    above: ProseRole?,
    role: ProseRole,
): Dp {
    val own = proseStyleOf(role).spaceBefore
    val previous = above?.let { proseStyleOf(it).spaceAfter } ?: 0.dp

    return maxOf(own, previous)
}

/**
 * Reveal: the raw source, markup visible, in the block's own type.
 *
 * The field is *controlled*: its value is rebuilt from the session and the caret on every
 * recomposition rather than held in local state. Local state was the obvious first shape and it is
 * wrong -- the block object changes identity on every keystroke, so a `remember` keyed on it resets
 * the value and sends the caret back to offset 0 mid-word. Deriving it means there is one copy of
 * the truth and nothing to fall out of step with it.
 */
@Composable
private fun RevealField(
    state: EditorState,
    id: BlockId,
    source: String,
    style: TextStyle,
    softWrapped: Boolean,
    modifier: Modifier = Modifier,
) {
    val caret = state.caret ?: return
    val span = state.blockOf(id)?.source
    val requester = remember { FocusRequester() }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicTextField(
        value = TextFieldValue(source, TextRange(caret.offset.coerceIn(0, source.length))),
        onValueChange = { edited ->
            if (span != null && edited.text != source) {
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
                    state.handle(event, caret.offset, source.length, layout.value)
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

/**
 * Preview: formatted, markup hidden, same metrics as reveal.
 *
 * `BasicText` with an [AnnotatedString], which is what 4.3 specifies. An earlier version used a
 * read-only text field so a click would land the caret directly -- but `TextFieldValue` carries a
 * plain `String`, so every span was silently dropped and the preview rendered unstyled. The styling
 * *is* the preview; losing it defeats the whole reveal/preview distinction.
 *
 * Three jobs beyond drawing the text. It reports its layout and position to the [SelectionLayer],
 * which is how a point becomes a position in the document. It draws its own share of the selection
 * highlight, clipped to itself, from its own `TextLayoutResult` -- 4.4's "highlight rectangles
 * drawn per block". And it stays keyboard-reachable, because pointer input is not the only way
 * into a document.
 */
@Composable
private fun PreviewText(
    state: EditorState,
    layer: SelectionLayer,
    id: BlockId,
    preview: BlockPreview,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // Keyed on the text, not on the `BlockPreview` holding it, so a preview rebuilt for any other
    // reason does not throw away a layout of identical text.
    var layout by remember(preview.text) { mutableStateOf<TextLayoutResult?>(null) }
    val highlight = LocalPalette.current.accent.copy(alpha = HIGHLIGHT_ALPHA)

    DisposableEffect(id) { onDispose { layer.onBlockRemoved(id) } }

    BasicText(
        text = preview.text,
        style = style,
        onTextLayout = { layout = it },
        modifier =
            modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    layout?.let { layer.onBlockPositioned(id, coordinates, it, preview) }
                }
                // The selection and the block's span are both read *here*, inside the draw lambda,
                // and not in composition. A draw-scope read invalidates drawing alone -- which is
                // what keeps a shifted block, whose span changed and whose glyphs did not, from
                // being composed again.
                .drawBehind {
                    val result = layout ?: return@drawBehind
                    val range = selectedPreviewRange(state, preview, id) ?: return@drawBehind
                    if (range.first < range.last) {
                        drawPath(result.getPathForRange(range.first, range.last), highlight)
                    }
                }.focusable()
                .onPreviewKeyEvent { event ->
                    val entering =
                        event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.Spacebar)
                    if (entering) state.place(Caret(id, 0))
                    entering
                },
    )
}

/**
 * The part of the selection that falls inside this block, in preview offsets.
 *
 * Clipping happens in document offsets, where the selection lives, and only then moves into the
 * block's own frame, which is where the preview map speaks.
 */
private fun selectedPreviewRange(
    state: EditorState,
    preview: BlockPreview,
    id: BlockId,
): IntRange? {
    // The selection is read first and bails out on its own. Reading the block as well would mean a
    // draw-scope read of the block list, and the block list changes on every keystroke -- which
    // would invalidate the drawing of every block on screen to paint a highlight that is not there.
    val selected = state.selectedSpan() ?: return null
    val own = state.blockOf(id)?.source

    return if (own == null) {
        null
    } else {
        val from = maxOf(selected.start.value, own.start.value)
        val to = minOf(selected.endExclusive.value, own.endExclusive.value)
        val start = own.start.value

        if (from < to) preview.previewRangeOf(from - start, to - start) else null
    }
}

/** Air above the first block and below the last, so the document does not start at the edge. */
private val documentPadding = 24.dp

/** Enough to read the highlight through, not so much that the text under it dims. */
private const val HIGHLIGHT_ALPHA = 0.25f

/**
 * How prominent one row is, per `appthere-drafts.md` 12's focus mode.
 *
 * One value rather than a pair of booleans, because the three states are exclusive and a row that
 * was somehow both focused and dimmed would be a contradiction the type can simply not express.
 */
internal enum class RowEmphasis(
    val alpha: Float,
) {
    /** Holds the caret. Drawn as an editable field rather than a preview. */
    Focused(1f),

    /** An ordinary row, at full contrast. What every row is when focus mode is off. */
    Normal(1f),

    /** Focus mode is on and the caret is elsewhere. */
    Dimmed(DIMMED_ALPHA),
}

/**
 * Nothing is dimmed until there is something to focus on.
 *
 * With no caret there is no current block, so dimming every row would leave a document that is
 * uniformly faint for no reason the reader could act on -- which is what would happen on every
 * launch, before anyone has clicked anything.
 */
private fun emphasisOf(
    id: BlockId,
    focused: BlockId?,
    focusMode: FocusMode,
): RowEmphasis =
    when {
        id == focused -> RowEmphasis.Focused
        focusMode == FocusMode.Off || focused == null -> RowEmphasis.Normal
        else -> RowEmphasis.Dimmed
    }

/**
 * Faint enough to recede, legible enough to still be read.
 *
 * 10.1 holds text to a contrast ratio, and this multiplies it -- so the floor is set by what the
 * palette had spare rather than by what looks calm on one theme.
 */
private const val DIMMED_ALPHA = 0.35f

/**
 * Where down the window the typewriter line sits, as a fraction of the viewport.
 *
 * Above the middle rather than on it. What a writer needs to see is the sentence they have just
 * finished and the shape of the paragraph it belongs to, which is above the caret; below it there
 * is nothing yet.
 */
private const val TYPEWRITER_LINE = 0.4f
