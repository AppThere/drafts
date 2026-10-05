package com.appthere.drafts.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * The height a block occupies in *both* states: the larger of the two, measured.
 *
 * `appthere-drafts.md` 4.2 makes this a hard requirement and prescribes the mitigation: "Since
 * markup characters are *added* in reveal state, a paragraph near a wrap boundary can gain a line.
 * Mitigation: reserve the reveal-state height for the focused block -- measure both states, use the
 * larger, and let preview state have a little slack at the bottom. This costs one extra measurement
 * pass per focus change and buys a completely still page."
 *
 * Every block reserves, not only the focused one. Reserving at the moment focus arrives would be
 * too late: the growth *is* the transition, and a block that reserved only while focused would
 * shrink again the instant focus left. The slack has to be there beforehand, which means it has to
 * be there always.
 *
 * One case makes the difference real, and it is the one 4.2 describes: a paragraph near a wrap
 * boundary, where the added markup characters push the last word onto a new line.
 *
 * There used to be a second and much larger one -- a paragraph the author hard-wrapped, which
 * preview reflowed and reveal did not -- and it accounted for 28% of the height of a real
 * screenplay. [ReflowNewlines] removes it at the source instead, so what is left for this to
 * reserve is the case the spec actually names. Measured across 26,000 words, that case arose zero
 * times; it is kept because "zero in one document" is not "never".
 *
 * Only the state that is *not* on screen is measured here. The one that is on screen is laid out by
 * its own text anyway, and the caller reserves this height as a minimum around it -- so the row
 * ends up at the larger of the two without the shown state being laid out twice. For almost every
 * row that means measuring the source; for the focused row, the preview. Measuring both used to
 * cost every row scrolled into view two layouts on top of its own, which was most of the price of
 * scrolling (measured 2026-10-03). It also makes this exactly what 4.2 prices it at: one extra
 * measurement when focus changes.
 *
 * Cost is bounded by the viewport, not the document: only composed blocks are measured, the result
 * is remembered against the text and the width, and [androidx.compose.ui.text.TextMeasurer] keeps
 * its own cache behind that.
 *
 * The hidden state is measured at its own width, [hiddenWidthPx]. In a document that is the width of
 * the shown one too; a screenplay line is the exception, edited full width and placed at its role's
 * insets only once the caret leaves it.
 *
 * A block whose two states are the same shape reserves nothing, which is why code blocks show their
 * fences rather than hiding them -- see `appendCode`.
 */
@Composable
internal fun reservedHeightOf(
    preview: AnnotatedString,
    source: String,
    revealed: Boolean,
    style: TextStyle,
    hiddenWidthPx: Int,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(preview, source, revealed, style, hiddenWidthPx) {
        val hidden = if (revealed) preview else AnnotatedString(source)
        val layout = measurer.measure(hidden, style = style, constraints = Constraints(maxWidth = hiddenWidthPx))

        with(density) { layout.size.height.toDp() }
    }
}
