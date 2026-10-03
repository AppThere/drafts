package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.platform.intents.DocumentKind
import kotlin.math.max

/**
 * The chrome along the top of the page: the document's status at the start, the window's panel
 * buttons at the end.
 *
 * One bar rather than two corners, because two corners do not know about each other. On a phone,
 * 7.4's *Untitled · Markdown* and the panel buttons do not fit on one line, and anchored to opposite
 * corners they simply ran into each other. Here the buttons drop to a second line when they would.
 *
 * Over the editor, not above it: the editor leaves room for the bar above its first block, and text
 * scrolled up passes underneath. That is what lets 12's fade give the whole height back -- a bar
 * above the editor would leave a strip of empty page while faded, on exactly the short windows 6
 * says auto-hiding matters most on. The bar is filled with the page's own colour so that text
 * passing under it is covered rather than drawn through the buttons, and the fill fades with them.
 */
@Composable
internal fun ChromeBar(
    hidden: Boolean,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        FadingChrome(hidden = hidden) {
            EdgeRow(
                gap = barGap,
                start = start,
                end = end,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(LocalPalette.current.background)
                        // No inset at the bottom: the editor's own air above the first block is
                        // the space between the chrome and the text.
                        .padding(start = controlsInset, top = controlsInset, end = controlsInset),
            )
        }
    }
}

/**
 * [start] at the start and [end] at the end of one line when both fit, and [end] on a line of its
 * own below when they do not -- still at the end, where the reader has learned to look for it.
 *
 * A `FlowRow` would wrap, but it puts a wrapped item at the start of its new line, which would move
 * the panel buttons to the opposite side of the window from where they are on every wider one.
 */
@Composable
private fun EdgeRow(
    gap: Dp,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box { start() }
            Box { end() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val first = measurables[0].measure(loose)
        val second = measurables[1].measure(loose)
        val space = gap.roundToPx()

        val width = constraints.maxWidth
        val oneLine = second.width == 0 || first.width + space + second.width <= width
        val height = if (oneLine) max(first.height, second.height) else first.height + space + second.height

        // `placeRelative`, so right to left mirrors the whole bar: the status at the right.
        layout(width, height) {
            if (oneLine) {
                first.placeRelative(0, (height - first.height) / 2)
                second.placeRelative(width - second.width, (height - second.height) / 2)
            } else {
                first.placeRelative(0, 0)
                second.placeRelative(width - second.width, first.height + space)
            }
        }
    }
}

/**
 * The start of the chrome bar: 8.4's status, and 7.4's kind while the document is untitled --
 * *Untitled · Markdown*, a control "until the first save".
 *
 * It wraps rather than overflowing: at 200% text the badge and the kind together can be wider than
 * a phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatusChrome(
    document: OpenDocument,
    kind: String,
    hidden: Boolean,
    onKindChange: ((DocumentKind) -> Unit)?,
    onSave: (() -> Unit)? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(barGap),
        verticalArrangement = Arrangement.spacedBy(barGap),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Faded chrome cannot be pressed, for the same reason the controls button cannot: a tap on
        // an empty-looking corner should not save a document.
        DocumentStateBadge(document.lifecycle.state, onSave = onSave.takeIf { !hidden })

        if (document.isUntitled && onKindChange != null) {
            KindSwitch(kind = kindOf(kind), enabled = !hidden, onChange = onKindChange)
        }
    }
}

/** Between the things in the bar, and between its lines when it wraps. */
private val barGap = 12.dp
