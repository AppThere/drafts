package com.appthere.drafts.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * A role turned into something Compose can set text in, plus the air around it.
 *
 * The space is Dp rather than a `TextUnit` because padding takes Dp -- but it is *derived* from the
 * role's size in sp, so it still scales with the reader's base size and with the OS font scale.
 * Space that stayed put while the text grew would close up the document at 200%.
 */
@Immutable
data class ProseStyle(
    val textStyle: TextStyle,
    val spaceBefore: Dp,
    val spaceAfter: Dp,
)

/**
 * Builds the style for a role from the reader's settings.
 *
 * Four of 5.5's controls land here rather than in the scale: the line-height multiplier, the
 * paragraph spacing and the letter spacing are applied on top of the role's own values, and the
 * body weight replaces the
 * role's weight for body text only -- a reader asking for lighter body text has not asked for
 * lighter headings, which are doing a different job.
 */
@Composable
fun proseStyleOf(
    role: ProseRole,
    settings: ReaderSettings = LocalReaderSettings.current,
    color: androidx.compose.ui.graphics.Color = LocalPalette.current.ink,
): ProseStyle {
    val size = role.sizeAt(settings.base)

    return ProseStyle(
        textStyle =
            TextStyle(
                color = color,
                fontSize = size,
                fontWeight = weightFor(role, settings),
                fontStyle = role.fontStyle,
                fontFamily = if (role.monospace) monoFontFamily() else proseFontFamily(),
                lineHeight = size * (role.lineHeight * lineHeightFactorOf(settings)),
                letterSpacing = (role.tracking + settings.letterSpacing).em,
                textAlign = TextAlign.Unspecified,
            ),
        spaceBefore = spaceOf(size, role.spaceBefore * spacingFactorOf(settings)),
        spaceAfter = spaceOf(size, role.spaceAfter * spacingFactorOf(settings)),
    )
}

/**
 * How far the reader has moved line height from the scale's own.
 *
 * Expressed as a factor so the whole scale moves together: a reader who opens body up to 2.0 gets
 * headings opened in the same proportion, rather than body air with tight headings. At the default
 * the factor is exactly one, so what renders is 5.2's table untouched.
 */
private fun lineHeightFactorOf(settings: ReaderSettings): Float = settings.lineHeight / Prose.Body.lineHeight

/** Paragraph spacing, as a factor on the scale's own -- one at the default, like the line height. */
private fun spacingFactorOf(settings: ReaderSettings): Float = settings.paragraphSpacing / Prose.Body.spaceAfter

/**
 * 5.5's body weight control, applied to body-weight roles only.
 *
 * "Font weight for body (300-500) -- low-vision support". A heading at 700 is bold because it is a
 * heading; dragging it around with the body control would flatten the hierarchy the scale exists
 * to create.
 */
private fun weightFor(
    role: ProseRole,
    settings: ReaderSettings,
): FontWeight = if (role.weight == FontWeight.W400) FontWeight(settings.bodyWeight) else role.weight

/**
 * Em of the role's own size, as Dp.
 *
 * `TextUnit` in sp already carries the font scale, and `value` reads it out in scale-independent
 * units -- so multiplying here and handing back Dp keeps the space proportional to the text at any
 * system scale, without needing a density.
 */
private fun spaceOf(
    size: TextUnit,
    em: Float,
): Dp = (size.value * em).dp
