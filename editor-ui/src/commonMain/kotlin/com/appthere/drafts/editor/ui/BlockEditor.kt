package com.appthere.drafts.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.FocusMode
import com.appthere.drafts.design.LocalMotion
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.LocalReaderSettings
import com.appthere.drafts.design.Measure
import com.appthere.drafts.design.ProseRole
import com.appthere.drafts.design.proseStyleOf
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.editor.engine.Caret

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
 *
 * [topInset] is room above the first block for chrome drawn over the editor: inside the list, so
 * text scrolls up under the chrome and has the whole height once it fades (12).
 */
@Composable
fun BlockEditor(
    state: EditorState,
    modifier: Modifier = Modifier,
    scroll: LazyListState = rememberLazyListState(),
    topInset: Dp = 0.dp,
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

        // A screenplay is 5.4's instead: its own column, and one size of type set from the width.
        val screenplay = if (state.screenplay) screenplayMeasureOf(maxWidth, settings.base) else null
        val column =
            screenplay?.column ?: Measure.of(maxWidth, with(density) { settings.base.toDp() }, settings.characters)

        val typewriter = LocalReaderSettings.current.typewriterScrolling
        val focused = state.caret?.block
        val fade = rememberRevealFade(focused, LocalMotion.current.revealMillis)

        FollowCaret(state, scroll, focused, typewriter)

        // 10.1's structural announcements, beside the list rather than in it: the list's rows come
        // and go as they scroll, and the thing speaking must not.
        StructureAnnouncer(state)

        val drawn = DocumentRows(state, layer, column.contentWidth, fade)

        // Every row's text styles read the base from the reader settings, so a screenplay's one
        // size reaches them there rather than through every row's signature.
        val rows = screenplay?.let { settings.copy(base = it.fontSize) } ?: settings
        CompositionLocalProvider(LocalReaderSettings provides rows) {
            LazyColumn(
                state = scroll,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { layer.onContainerPositioned(it) }
                        .focusRequester(focus)
                        .focusable()
                        .selectionInput(state, layer, focus),
                contentPadding =
                    PaddingValues(
                        start = column.gutter,
                        top = documentPadding + topInset,
                        end = column.gutter,
                        bottom = documentPadding,
                    ),
                // 5.3: "centred with generous margins on a desktop window". The column stops at its
                // measure and the leftover becomes margin on both sides rather than all on the right.
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val blocks = state.blocks
                val rows = state.rows

                // Keyed by a block's id, and a folded pair by its first block's: when the caret
                // arrives in a pair and it unfolds, the pair's first line keeps the pair's place.
                items(rows.size, key = { blocks[rows.blockAt(it)].id.value }) { row -> drawn.ListRow(row) }
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
internal fun BlockRow(
    state: EditorState,
    layer: SelectionLayer,
    id: BlockId,
    content: RowContent,
    emphasis: RowEmphasis,
    fade: Animatable<Float, AnimationVector1D>?,
    geometry: RowGeometry,
    dual: DualPart?,
    modifier: Modifier = Modifier,
) {
    val prose = proseStyleOf(content.role)
    val revealed = emphasis == RowEmphasis.Focused

    // 5.4's insets, fractions of the column: a character a third of the way across, dialogue in a
    // narrower band. The preview is laid out in what is left. The line being edited is not: a
    // screenplay's roles turn on what is typed -- a name is action until someone speaks under it --
    // so it is written full width, on a tint that says it will be placed when the caret leaves.
    val insets = PaddingValues(start = geometry.insetStart, end = geometry.insetEnd)
    val editing = revealed && state.screenplay
    val previewWidth =
        with(LocalDensity.current) { (geometry.width - geometry.insetStart - geometry.insetEnd).roundToPx() }
    val revealWidth = if (state.screenplay) with(LocalDensity.current) { geometry.width.roundToPx() } else previewWidth
    // A stacked dual pair's rule and marker, which the line being written goes without: it is set
    // full width, across the margin they are drawn in.
    val marked = dual?.takeIf { !revealed }
    val rule = LocalPalette.current.muted

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
            .widthIn(max = geometry.width)
            .fillMaxWidth()
            .then(marked?.let { Modifier.dualRule(it, geometry.width, geometry.spaceBefore, rule) } ?: Modifier)
            // 5.2 gives each role its own space before and after, so a heading brings its own air
            // with it. The gap between two blocks is the *larger* of the pair, not the sum: the
            // table reads as CSS margins, and CSS collapses adjacent vertical margins. Adding them
            // gave a document with visibly more air between every pair of blocks than 5.2 asks for.
            .padding(top = geometry.spaceBefore, bottom = geometry.spaceAfter),
    ) {
        // The hidden state at the width it would be laid out under, which is what makes the
        // reserved height the right one.
        // Measured against the source *as reveal draws it*: newlines are marked rather than
        // obeyed, so the two states differ only by the markup characters -- which is the case 4.2
        // was actually written for, and a far smaller one.
        val reserved =
            reservedHeightOf(
                preview = content.preview.text,
                source = if (content.softWrapped) reflowedForDisplay(content.source) else content.source,
                revealed = revealed,
                style = prose.textStyle,
                hiddenWidthPx = if (revealed) previewWidth else revealWidth,
            )

        Box(Modifier.fillMaxWidth().heightIn(min = reserved).then(if (editing) Modifier.editingTint() else Modifier)) {
            // 4.2: "Cross-fade inline decoration over 120ms with no layout animation. Because block
            // metrics are identical in both states (4.1), nothing moves -- only glyph styling
            // changes. Respect `prefers-reduced-motion`: at reduced motion the switch is
            // instantaneous."
            //
            // No layout animation is the reserved height above, which both states already share.
            // This fades only what is drawn inside it, and only for the two blocks the caret is
            // moving between; see `RevealFade`.
            RevealContent(
                revealed = revealed,
                fade = fade,
                preview = {
                    PreviewText(
                        state = state,
                        layer = layer,
                        id = id,
                        preview = content.preview,
                        style = prose.textStyle,
                        modifier = Modifier.padding(insets).spoken(content.spoken),
                    )
                },
                reveal = {
                    RevealField(
                        state = state,
                        id = id,
                        source = content.source,
                        style =
                            if (state.screenplay) {
                                prose.textStyle.copy(
                                    textAlign = TextAlign.Start,
                                )
                            } else {
                                prose.textStyle
                            },
                        softWrapped = content.softWrapped,
                        modifier = if (state.screenplay) Modifier else Modifier.padding(insets),
                    )
                },
            )
            if (marked == DualPart.Simultaneous) {
                SimultaneousMarker(
                    width = geometry.insetStart,
                    style = prose.textStyle,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}

/**
 * The tint behind the screenplay line being edited ([com.appthere.drafts.design.Palette.editing]).
 *
 * Drawn a little past the text on either side, into the margin, so the line does not sit flush
 * against the edge of its band; nothing is laid out differently for it.
 */
@Composable
private fun Modifier.editingTint(): Modifier {
    val colour = LocalPalette.current.editing
    return drawBehind {
        val bleed = tintBleed.toPx()
        drawRoundRect(
            color = colour,
            topLeft = Offset(-bleed, 0f),
            size = Size(size.width + bleed * 2, size.height),
            cornerRadius = CornerRadius(bleed / 2),
        )
    }
}

/** How far the editing tint reaches past the text into the margin. The gutter is never under 16dp. */
private val tintBleed = 8.dp

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
internal fun emphasisOf(
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
