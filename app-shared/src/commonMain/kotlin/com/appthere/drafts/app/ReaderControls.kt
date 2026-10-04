package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.FocusMode
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Measure
import com.appthere.drafts.design.MotionPreference
import com.appthere.drafts.design.Palette
import com.appthere.drafts.design.Palettes
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.Theme
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.body_weight
import com.appthere.drafts.i18n.resources.decimal_separator
import com.appthere.drafts.i18n.resources.decrease
import com.appthere.drafts.i18n.resources.focus
import com.appthere.drafts.i18n.resources.focus_block
import com.appthere.drafts.i18n.resources.focus_off
import com.appthere.drafts.i18n.resources.increase
import com.appthere.drafts.i18n.resources.less
import com.appthere.drafts.i18n.resources.letter_spacing
import com.appthere.drafts.i18n.resources.line_height
import com.appthere.drafts.i18n.resources.measure
import com.appthere.drafts.i18n.resources.more
import com.appthere.drafts.i18n.resources.motion
import com.appthere.drafts.i18n.resources.motion_full
import com.appthere.drafts.i18n.resources.motion_reduced
import com.appthere.drafts.i18n.resources.motion_system
import com.appthere.drafts.i18n.resources.paragraph_spacing
import com.appthere.drafts.i18n.resources.reader_controls
import com.appthere.drafts.i18n.resources.said_after
import com.appthere.drafts.i18n.resources.settings_not_saved
import com.appthere.drafts.i18n.resources.text_size
import com.appthere.drafts.i18n.resources.theme
import com.appthere.drafts.i18n.resources.theme_dark
import com.appthere.drafts.i18n.resources.theme_high_contrast
import com.appthere.drafts.i18n.resources.theme_light
import com.appthere.drafts.i18n.resources.theme_sepia
import com.appthere.drafts.i18n.resources.theme_system
import com.appthere.drafts.i18n.resources.typewriter
import com.appthere.drafts.i18n.resources.typewriter_off
import com.appthere.drafts.i18n.resources.typewriter_on
import com.appthere.drafts.i18n.resources.value_em
import com.appthere.drafts.i18n.resources.value_sp
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The reader controls of `appthere-drafts.md` 5.5.
 *
 * Every one of them exists for somebody. The spec names line height and letter spacing as dyslexia
 * support and body weight as low-vision support, and 10.2 says to "offer the weight, line-height,
 * and letter-spacing controls from 5.5 prominently, not buried" -- so they sit in the same panel
 * as the size and the theme rather than behind an "advanced" disclosure.
 *
 * **Steppers, not sliders.** 10.2 requires "complete keyboard operation" and that "dragging ...
 * always has a non-drag alternative". A slider needs that alternative built anyway; a pair of
 * buttons *is* the alternative, is reachable by Tab, has a 48dp target, and lands on an exact
 * value rather than near one -- which matters when the value is a font size someone has found by
 * trial and wants to return to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderControls(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    modifier: Modifier = Modifier,
    unsaved: Boolean = false,
    onClose: (() -> Unit)? = null,
    links: @Composable () -> Unit = {},
) {
    val palette = LocalPalette.current
    val metrics = panelMetrics()

    // Read here rather than inside `semantics`, which is not a composition.
    val title = stringResource(Res.string.reader_controls)

    Column(
        modifier
            // A ceiling, not a width. 6 makes Compact windows -- under 600dp -- a first-class case,
            // and a panel that insisted on its natural width would hang off the side of a phone,
            // or off the side of any window once the system scale doubled it. Capped first and
            // filled second: the other way round, `fillMaxWidth` fixes the width and the cap has
            // nothing left to do.
            // No ceiling on a Compact window: 6 asks for settings "as modal sheets" there, and a
            // sheet that stopped short of both edges is a panel that has been pushed to the bottom.
            // A pane, and named as one. Assistive technology announces a pane by its title when it
            // appears, which is what tells a screen reader that the settings have opened rather
            // than that focus has moved somewhere unexplained.
            .semantics { paneTitle = title }
            .widthIn(max = metrics.panel)
            .fillMaxWidth()
            .background(palette.background)
            .border(hairline, palette.muted, RoundedCornerShape(corner))
            .padding(panelPadding)
            // Scrolls rather than clips. At 200% system scale the panel ran off the bottom of its
            // own frame and the last four controls simply were not there -- including the motion
            // control, which someone at 200% is more likely to need than most.
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        if (onClose != null) {
            PanelHeader(title = stringResource(Res.string.reader_controls), onClose = onClose)
        } else {
            BasicText(stringResource(Res.string.reader_controls), style = heading(palette))
        }

        // A failed write of the settings file. Said here, where the reader is changing them, and
        // in words about what it means for them rather than what went wrong on disk: the change
        // has happened, and will be gone next time. A polite live region, so a screen reader
        // hears it without being interrupted mid-announcement of the control just pressed.
        if (unsaved) {
            BasicText(
                text = stringResource(Res.string.settings_not_saved),
                style = body(palette),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        ThemeChoice(settings, onChange)

        TypeSteppers(settings, onChange)

        TypewriterChoice(settings, onChange)

        FocusChoice(settings, onChange)

        MotionChoice(settings, onChange)

        // Ways on to the other panels: 5.1's licences, which have to be reachable, and 10.2's
        // shortcut list. Here rather than in a menu the app does not have yet, and the caller's to
        // supply, since which panels there are is the window's business rather than the panel's.
        links()
    }
}

/**
 * The controls of 5.5 that set the type itself, each a number with a range.
 *
 * In the order 5.5 lists them, which is also roughly the order a reader reaches for them: size
 * first, then the air in and between lines, then the line's length and the weight of its ink.
 */
@Composable
private fun TypeSteppers(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        Stepper(
            label = stringResource(Res.string.text_size),
            value =
                stringResource(
                    Res.string.value_sp,
                    settings.base.value
                        .roundToInt()
                        .toString(),
                ),
            onLess = { onChange(settings.copy(base = (settings.base.value - 1).sp).clamped()) },
            onMore = { onChange(settings.copy(base = (settings.base.value + 1).sp).clamped()) },
        )

        Stepper(
            label = stringResource(Res.string.line_height),
            value = format(settings.lineHeight),
            onLess = { onChange(settings.copy(lineHeight = settings.lineHeight - LINE_HEIGHT_STEP).clamped()) },
            onMore = { onChange(settings.copy(lineHeight = settings.lineHeight + LINE_HEIGHT_STEP).clamped()) },
        )

        Stepper(
            label = stringResource(Res.string.letter_spacing),
            value = stringResource(Res.string.value_em, format(settings.letterSpacing)),
            onLess = { onChange(settings.copy(letterSpacing = settings.letterSpacing - SPACING_STEP).clamped()) },
            onMore = { onChange(settings.copy(letterSpacing = settings.letterSpacing + SPACING_STEP).clamped()) },
        )

        Stepper(
            label = stringResource(Res.string.measure),
            // A count, with no unit after it: the label already says what is being counted.
            value = settings.characters.roundToInt().toString(),
            onLess = { onChange(settings.copy(characters = settings.characters - MEASURE_STEP).clamped()) },
            onMore = { onChange(settings.copy(characters = settings.characters + MEASURE_STEP).clamped()) },
        )

        Stepper(
            label = stringResource(Res.string.paragraph_spacing),
            value = stringResource(Res.string.value_em, format(settings.paragraphSpacing)),
            onLess = {
                onChange(
                    settings.copy(paragraphSpacing = settings.paragraphSpacing - PARAGRAPH_STEP).clamped(),
                )
            },
            onMore = {
                onChange(
                    settings.copy(paragraphSpacing = settings.paragraphSpacing + PARAGRAPH_STEP).clamped(),
                )
            },
        )

        Stepper(
            label = stringResource(Res.string.body_weight),
            // A weight on the usual 100-900 scale, which is a number and not an amount of
            // anything: 400 is 400 in every language.
            value = settings.bodyWeight.toString(),
            onLess = { onChange(settings.copy(bodyWeight = settings.bodyWeight - WEIGHT_STEP).clamped()) },
            onMore = { onChange(settings.copy(bodyWeight = settings.bodyWeight + WEIGHT_STEP).clamped()) },
        )
    }
}

/** 5.5: "Theme: light, dark, sepia, high contrast, system". */
@Composable
private fun ThemeChoice(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    Choice(
        label = stringResource(Res.string.theme),
        options = Theme.all.map { Option(it, labelOf(it)) },
        selected = settings.theme,
        onSelect = { theme -> onChange(settings.copy(theme = theme)) },
    )
}

/**
 * 12: "**Typewriter scrolling** as an option: keep the caret at a fixed vertical position."
 *
 * Both options are offered here rather than hidden behind a preferences window, because 12 calls
 * them options and 5.5 already put the reading controls one shortcut away. A reader who finds the
 * page moving under them should be able to stop it without going looking.
 */
@Composable
private fun TypewriterChoice(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    Choice(
        label = stringResource(Res.string.typewriter),
        options =
            listOf(
                Option(false, stringResource(Res.string.typewriter_off)),
                Option(true, stringResource(Res.string.typewriter_on)),
            ),
        selected = settings.typewriterScrolling,
        onSelect = { moves -> onChange(settings.copy(typewriterScrolling = moves)) },
    )
}

/** 12: "**Focus mode** as an option: dim all blocks except the current one". */
@Composable
private fun FocusChoice(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    Choice(
        label = stringResource(Res.string.focus),
        options =
            listOf(
                Option(FocusMode.Off, stringResource(Res.string.focus_off)),
                Option(FocusMode.Block, stringResource(Res.string.focus_block)),
            ),
        selected = settings.focusMode,
        onSelect = { mode -> onChange(settings.copy(focusMode = mode)) },
    )
}

/** 10.2 asks for `prefers-reduced-motion` to be honoured; this is the reader's own say in it. */
@Composable
private fun MotionChoice(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    Choice(
        label = stringResource(Res.string.motion),
        options = motionOrder.map { Option(it, labelOf(it)) },
        selected = settings.motion,
        onSelect = { preference -> onChange(settings.copy(motion = preference)) },
    )
}

/**
 * A label, the current value, and a button either side of it.
 *
 * The value is announced with the label rather than on its own, so a screen reader says "Text size,
 * 18sp" instead of "18sp" next to something it has already moved past.
 */
@Composable
private fun Stepper(
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
        Button(stringResource(Res.string.less), fewer, onLess)
        BasicText(
            text = value,
            style = body(palette).copy(textAlign = TextAlign.Center),
            modifier = Modifier.width(panelMetrics().value),
        )
        Button(stringResource(Res.string.more), more, onMore)
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
private data class Option<T>(
    val value: T,
    val label: String,
)

/** A row of mutually exclusive options, each one a target in its own right, wrapping if it must. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(
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
private fun Button(
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

/**
 * Two decimals, without pulling in a formatting library for one panel.
 *
 * The separator comes from 11.1's resources because there is no locale-aware number formatter in
 * common Kotlin, and a hardcoded full stop reads as a thousands separator to most of Europe. The
 * grouping of a whole part is not handled at all, which costs nothing here: every number this
 * formats is between zero and two.
 */
@Composable
private fun format(value: Float): String {
    val hundredths = (value * HUNDRED).roundToInt()
    val fraction = (hundredths % HUNDRED).toString().padStart(2, '0')

    return "${hundredths / HUNDRED}${stringResource(Res.string.decimal_separator)}$fraction"
}

@Composable
private fun heading(palette: Palette) =
    interfaceTextStyle(color = palette.ink, fontSize = headingSize, fontWeight = Prose.H4.weight)

@Composable
private fun body(palette: Palette) = interfaceTextStyle(color = palette.ink, fontSize = labelSize)

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
private fun panelMetrics(): PanelMetrics {
    val label = with(LocalDensity.current) { labelSize.toDp() }
    return PanelMetrics(
        panel = label * PANEL_LABELS,
        label = label * LABEL_LABELS,
        value = label * VALUE_LABELS,
    )
}

@Immutable
private data class PanelMetrics(
    val panel: Dp,
    val label: Dp,
    val value: Dp,
)

private const val PANEL_LABELS = 30f
private const val LABEL_LABELS = 9f
private const val VALUE_LABELS = 5f

private val panelPadding = 16.dp
private val rowGap = 8.dp
private val optionGap = 6.dp
private val optionPadding = 10.dp
private val buttonPadding = 12.dp
private val corner = 6.dp
private val hairline = 1.dp
private val target = 48.dp
private val labelSize = 14.sp
private val headingSize = 18.sp

private const val LINE_HEIGHT_STEP = 0.1f
private const val SPACING_STEP = 0.01f
private const val PARAGRAPH_STEP = 0.25f
private const val MEASURE_STEP = 5f
private const val WEIGHT_STEP = 50
private const val HUNDRED = 100

/**
 * The three motion choices, as words.
 *
 * "Follow the system" is offered first because it is the default, and because 10.2's promise is
 * that a reader who has already told their operating system does not have to tell this as well.
 */
@Composable
private fun labelOf(preference: MotionPreference): String =
    when (preference) {
        MotionPreference.Full -> stringResource(Res.string.motion_full)
        MotionPreference.Reduced -> stringResource(Res.string.motion_reduced)
        MotionPreference.System -> stringResource(Res.string.motion_system)
    }

/** Follow-the-system first: it is the default, and 10.2's promise that asking once is enough. */
private val motionOrder =
    listOf(MotionPreference.System, MotionPreference.Full, MotionPreference.Reduced)

/**
 * A theme as the reader sees it.
 *
 * Deliberately not `Theme.id`. That is the key their choice is saved under and it must survive
 * 11.1's translation; this is the word, and words change language. Keeping them the same string
 * would mean a reader who switched the application to French lost the theme they had chosen.
 */
@Composable
private fun labelOf(theme: Theme): String =
    when (theme.id) {
        Palettes.Light.id -> stringResource(Res.string.theme_light)
        Palettes.Dark.id -> stringResource(Res.string.theme_dark)
        Palettes.Sepia.id -> stringResource(Res.string.theme_sepia)
        Palettes.HighContrast.id -> stringResource(Res.string.theme_high_contrast)
        else -> stringResource(Res.string.theme_system)
    }
