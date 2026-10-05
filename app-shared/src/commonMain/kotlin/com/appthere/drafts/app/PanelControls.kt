package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Palette
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.decrease
import com.appthere.drafts.i18n.resources.increase
import com.appthere.drafts.i18n.resources.less
import com.appthere.drafts.i18n.resources.more
import com.appthere.drafts.i18n.resources.said_after
import org.jetbrains.compose.resources.stringResource

/*
 * What the panels over a document are built from: their frame, and the controls in them -- a row
 * of options, a stepper -- with the sizes 10.2 holds them to. Shared so that every panel is the
 * same kind of thing to a reader, and to a screen reader, rather than each drawing its own.
 */

/**
 * A panel's frame: a pane titled [title], capped in width, bordered, and scrolling.
 *
 * A ceiling, not a width. 6 makes Compact windows -- under 600dp -- a first-class case, and a panel
 * that insisted on its natural width would hang off the side of a phone, or off the side of any
 * window once the system scale doubled it. Capped first and filled second: the other way round,
 * `fillMaxWidth` fixes the width and the cap has nothing left to do. No ceiling on a Compact window:
 * 6 asks for settings "as modal sheets" there, and a sheet that stopped short of both edges is a
 * panel that has been pushed to the bottom.
 *
 * A pane, and named as one. Assistive technology announces a pane by its title when it appears,
 * which is what tells a screen reader that the panel has opened rather than that focus has moved
 * somewhere unexplained.
 *
 * Scrolls rather than clips. At 200% system scale the reader controls ran off the bottom of their
 * own frame and the last four controls simply were not there -- including the motion control, which
 * someone at 200% is more likely to need than most.
 */
@Composable
internal fun Modifier.panelFrame(title: String): Modifier {
    val palette = LocalPalette.current
    return semantics { paneTitle = title }
        .widthIn(max = panelMetrics().panel)
        .fillMaxWidth()
        .background(palette.background)
        .border(hairline, palette.muted, RoundedCornerShape(corner))
        .padding(panelPadding)
        .verticalScroll(rememberScrollState())
}

/**
 * A label, the current value, and a button either side of it.
 *
 * The value is announced with the label rather than on its own, so a screen reader says "Text size,
 * 18sp" instead of "18sp" next to something it has already moved past.
 */
@Composable
internal fun Stepper(
    label: String,
    value: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    val palette = LocalPalette.current
    val said = stringResource(Res.string.said_after, label, value)
    val fewer = stringResource(Res.string.said_after, label, stringResource(Res.string.decrease))
    val more = stringResource(Res.string.said_after, label, stringResource(Res.string.increase))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = label,
            style = body(palette),
            modifier =
                Modifier
                    .width(panelMetrics().label)
                    .semantics { contentDescription = said },
        )
        StepButton(stringResource(Res.string.less), fewer, onLess)
        BasicText(
            text = value,
            style = body(palette).copy(textAlign = TextAlign.Center),
            modifier = Modifier.width(panelMetrics().value),
        )
        StepButton(stringResource(Res.string.more), more, onMore)
    }
}

/**
 * One of a set of options, as the reader sees it and as the application means it.
 *
 * The two are separate because 11.1 translates the first and must not touch the second. This used
 * to be one string: the control offered words, and the handler matched on the words to decide what
 * had been chosen -- so the first translation would have made every option unrecognisable, silently
 * and only in the other language.
 */
@Immutable
internal data class Option<T>(
    val value: T,
    val label: String,
)

/** A row of mutually exclusive options, each one a target in its own right, wrapping if it must. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Choice(
    label: String,
    options: List<Option<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val palette = LocalPalette.current

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        BasicText(label, style = body(palette), modifier = Modifier.width(panelMetrics().label))
        // Wraps, so a row of options that will not fit across becomes two rows rather than
        // running off the side.
        //
        // Defensive, and honestly so: this one is *not* covered by a test. Compose clips children
        // to their parent and `getBoundsInRoot` reports the clipped bounds, so a row overflowing
        // its panel looks identical from a test to one that fits. What it does is visible in a
        // screenshot at a constrained width -- "High contrast" moves to its own row -- and the
        // cost of keeping it is nil, but nothing here will notice if it stops working.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(optionGap),
            verticalArrangement = Arrangement.spacedBy(optionGap),
        ) {
            options.forEach { option ->
                val chosen = option.value == selected
                val described = stringResource(Res.string.said_after, label, option.label)

                BasicText(
                    text = option.label,
                    style = body(palette).copy(color = if (chosen) palette.background else palette.ink),
                    modifier =
                        Modifier
                            .sizeIn(minWidth = target, minHeight = target)
                            .background(
                                if (chosen) palette.accent else palette.background,
                                RoundedCornerShape(corner),
                            ).border(hairline, palette.muted, RoundedCornerShape(corner))
                            .clickable { onSelect(option.value) }
                            .padding(horizontal = optionPadding, vertical = optionPadding)
                            .semantics { contentDescription = described },
                )
            }
        }
    }
}

/** 10.2: "Touch targets >= 48dp." A stepper button is the smallest thing here, so it sets the floor. */
@Composable
private fun StepButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current

    BasicText(
        text = glyph,
        style = body(palette).copy(textAlign = TextAlign.Center),
        modifier =
            Modifier
                .sizeIn(minWidth = target, minHeight = target)
                .border(hairline, palette.muted, RoundedCornerShape(corner))
                .clickable { onClick() }
                .padding(buttonPadding)
                .semantics { contentDescription = description },
    )
}

@Composable
internal fun heading(palette: Palette) =
    interfaceTextStyle(color = palette.ink, fontSize = headingSize, fontWeight = Prose.H4.weight)

@Composable
internal fun body(palette: Palette) = interfaceTextStyle(color = palette.ink, fontSize = labelSize)

/**
 * The panel's widths, in multiples of its own label size.
 *
 * They used to be fixed dp, on the reasoning that chrome should not grow with the reader's text.
 * That reasoning was wrong, and 200% system scale showed why: 10.2 requires `sp` for all text, so
 * the labels double whatever the frame does. A container that does not follow them does not stay
 * compact -- it clips, wraps a theme name one letter to a line, and drops the controls that do not
 * fit off the bottom.
 *
 * Multiples of the label size rather than of the reader's base size: this is chrome, and it follows
 * the *system* scale, not the document setting the reader is in the middle of adjusting.
 */
@Composable
internal fun panelMetrics(): PanelMetrics {
    val label = with(LocalDensity.current) { labelSize.toDp() }
    return PanelMetrics(
        panel = label * PANEL_LABELS,
        label = label * LABEL_LABELS,
        value = label * VALUE_LABELS,
    )
}

@Immutable
internal data class PanelMetrics(
    val panel: Dp,
    val label: Dp,
    val value: Dp,
)

private const val PANEL_LABELS = 30f
private const val LABEL_LABELS = 9f
private const val VALUE_LABELS = 5f

private val panelPadding = 16.dp
private val optionGap = 6.dp
private val optionPadding = 10.dp
private val buttonPadding = 12.dp
private val corner = 6.dp
private val hairline = 1.dp
private val target = 48.dp
private val labelSize = 14.sp
private val headingSize = 18.sp
