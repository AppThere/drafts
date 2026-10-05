package com.appthere.drafts.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Screenplay
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.block_simultaneous
import org.jetbrains.compose.resources.stringResource

/** One block of a side-by-side pair, placed by [DualRow] in a column [half] wide. */
internal typealias DualBlock = @Composable (index: Int, half: Dp) -> Unit

/**
 * A [DualPair] set side by side: 5.4's "two-column layout at the 16.7%/25% insets, splitting the
 * available width". The pair brings the space above and below it; each speech's lines have none
 * between them, on the page or here.
 *
 * Only where the window has room. "On a compact window it stacks vertically with a connecting rule
 * and a 'simultaneous' marker", which is the pair's blocks one under another, as an unfolded pair's
 * are (`dualRule`, [SimultaneousMarker]).
 *
 * Each column is a traversal group, so a screen reader reads one speech and then the other rather
 * than across the two a line at a time.
 */
@Composable
internal fun DualRow(
    pair: DualPair,
    columnWidth: Dp,
    spaceBefore: Dp,
    spaceAfter: Dp,
    block: DualBlock,
    modifier: Modifier = Modifier,
) {
    val band = columnWidth * (1f - Screenplay.Dialogue.insetStart - Screenplay.Dialogue.insetEnd)
    val gap = columnWidth * DUAL_GAP
    val half = (band - gap) / 2

    Row(
        modifier
            .widthIn(max = columnWidth)
            .fillMaxWidth()
            .padding(top = spaceBefore, bottom = spaceAfter)
            .padding(
                start = columnWidth * Screenplay.Dialogue.insetStart,
                end = columnWidth * Screenplay.Dialogue.insetEnd,
            ),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        listOf(pair.first, pair.second).forEach { speech ->
            Column(Modifier.width(half).semantics { isTraversalGroup = true }) {
                speech.forEach { block(it, half) }
            }
        }
    }
}

/**
 * The rule that runs beside a stacked pair, in the margin dialogue leaves at the start of the line.
 *
 * Drawn by every row of the pair, each over its whole height, so the segments meet into one line:
 * from the first name's text down past the space between the two speeches to the end of the second.
 * Only the opening row skips the space above it, which belongs to whatever came before the pair.
 */
internal fun Modifier.dualRule(
    part: DualPart,
    columnWidth: Dp,
    spaceBefore: Dp,
    colour: Color,
): Modifier =
    drawBehind {
        val x = (columnWidth * Screenplay.Dialogue.insetStart - ruleGap).toPx()
        val at = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
        val top = if (part == DualPart.Opening) spaceBefore.toPx() else 0f
        drawLine(colour, Offset(at, top), Offset(at, size.height), strokeWidth = ruleWidth.toPx())
    }

/**
 * 5.4's "simultaneous" marker, in the margin a character name leaves before itself.
 *
 * Set in that margin rather than above the name so that it takes no height: the row is the height
 * it would be without it, and nothing below moves for it. Silent, because the name already says it --
 * its description begins "Simultaneous" wherever the pair is (`spokenOf`), side by side or not.
 */
@Composable
internal fun SimultaneousMarker(
    width: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = stringResource(Res.string.block_simultaneous),
        style =
            style.copy(
                color = LocalPalette.current.muted,
                fontSize = style.fontSize * MARKER_SCALE,
                textAlign = TextAlign.Start,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.width((width - ruleGap).coerceAtLeast(0.dp)).semantics { hideFromAccessibility() },
    )
}

/** The space between the two columns: two of the 61 characters a line carries. */
private const val DUAL_GAP = 2f / 61f

/** The marker is a note in the margin, not a line of the script. */
private const val MARKER_SCALE = 0.75f

/** How far the rule stands off the dialogue, and the marker off the name. */
private val ruleGap = 12.dp

private val ruleWidth = 1.dp
